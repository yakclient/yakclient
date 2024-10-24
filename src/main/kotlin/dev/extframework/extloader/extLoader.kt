package dev.extframework.extloader

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.extloader.environment.registerBasicSerializers
import dev.extframework.extloader.environment.registerLoaders
import dev.extframework.extloader.exception.BasicExceptionPrinter
import dev.extframework.extloader.exception.handleException
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.ExtensionLoadException
import dev.extframework.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.exception.StackTracePrinter
import dev.extframework.internal.api.exception.StructuredException
import dev.extframework.internal.api.extension.*
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

public class InternalExtensionEnvironment private constructor() : ExtensionEnvironment() {
    public val workingDir: Path
        get() = get(wrkDirAttrKey).extract().value
    public val archiveGraph: ArchiveGraph
         get() = get(ArchiveGraphAttribute).extract().graph
    public val dependencyTypes: DependencyTypeContainer
        get() = get(dependencyTypesAttrKey).extract().container
    public val application: ApplicationTarget by get(ApplicationTarget)
    public val extensionResolver: ExtensionResolver by get(ExtensionResolver)

    public constructor(
        workingDir: Path,
        archiveGraph: ArchiveGraph,
        dependencyTypes: DependencyTypeContainer,
        applicationTarget: ApplicationTarget,
        extensionResolver: ExtensionResolver,
    ) : this() {
        add(ValueAttribute(workingDir, wrkDirAttrKey))
        add(ArchiveGraphAttribute(archiveGraph))
        add(DependencyTypeContainerAttribute(dependencyTypes))
        add(applicationTarget)
        add(extensionResolver)
    }

    public constructor(
        workingDir: Path,
        archiveGraph: ArchiveGraph,
        dependencyTypes: DependencyTypeContainer,
        applicationTarget: ApplicationTarget,
    ) : this() {
        add(ValueAttribute(workingDir, wrkDirAttrKey))
        add(ArchiveGraphAttribute(archiveGraph))
        add(DependencyTypeContainerAttribute(dependencyTypes))
        add(applicationTarget)
        add(DefaultExtensionResolver(ClassLoader.getSystemClassLoader(), this))
    }
}

public fun initExtensions(
    extensionRequests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,

    environment: InternalExtensionEnvironment,
): Job<Unit> = job {
    environment += ValueAttribute(
        ClassLoader.getSystemClassLoader(),
        parentCLAttrKey
    )

    environment.addUnless(object : ExtensionClassLoaderProvider {})

    environment.addUnless(MutableObjectContainerAttribute(partitionLoadersAttrKey))
    environment[partitionLoadersAttrKey].extract().registerLoaders()

    environment.addUnless(MutableObjectSetAttribute(exceptionCxtSerializersAttrKey).apply {
        registerBasicSerializers()
    })
    environment.addUnless(BasicExceptionPrinter())

    initExtensions(environment, extensionRequests)().handleStructuredException(environment)
}

private fun initExtensions(
    environment: InternalExtensionEnvironment,
    extensionRequests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,
) = job {
    fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
        return node.access.targets.map { it.relationship.node }
            .filterIsInstance<ExtensionNode>()
            .flatMapTo(HashSet(), ::allExtensions) + node
    }

    val extensionResolver = environment.extensionResolver

    fun loadTweakers(
        artifact: Artifact<ExtensionArtifactMetadata>
    ): AsyncJob<List<ExtensionPartitionContainer<TweakerPartitionNode, *>>> = asyncJob {
        val parents =
            artifact.parents.mapAsync {
                loadTweakers(it)().merge()
            }

        val tweakerContainer: ExtensionPartitionContainer<TweakerPartitionNode, *>? = run {
            val descriptor = PartitionDescriptor(artifact.metadata.descriptor, TweakerPartitionLoader.TYPE)

            val cacheResult = environment.archiveGraph.cacheAsync(
                PartitionArtifactRequest(descriptor),
                artifact.metadata.repository,
                extensionResolver.partitionResolver,
            )()
            if (cacheResult.isFailure && cacheResult.exceptionOrNull() is ArchiveException.ArchiveNotFound) return@run null
            else cacheResult.merge()

            environment.archiveGraph.get(
                descriptor,
                extensionResolver.partitionResolver,
            )().merge()
        } as? ExtensionPartitionContainer<TweakerPartitionNode, *>

        parents.awaitAll().flatten() + listOfNotNull(tweakerContainer)
    }

    val tweakers = job(JobName("Load tweakers")) {
        runBlocking(Dispatchers.IO) {
            extensionRequests.flatMap { (ext, repo) ->
                asyncJob {
                    val artifact = extensionResolver.createContext(repo)
                        .getAndResolveAsync(
                            ExtensionArtifactRequest(ext)
                        )().merge()

                    loadTweakers(artifact)().merge()
                }().mapException {
                    ExtensionLoadException(ext, it) {
                        ext asContext "Extension"
                    }
                }.merge()
            }
        }
    }().merge()

    tweakers.map { it.node }.forEach {
        it.tweaker.tweak(environment)().merge()
    }

    val extensionNodes = job(JobName("Load extensions")) {
        extensionRequests.map { (ext, repo) ->
            job {
                environment.archiveGraph.cache(
                    ExtensionArtifactRequest(
                        ext,
                    ),
                    repo,
                    extensionResolver
                )().merge()

                environment.archiveGraph.get(
                    ext,
                    extensionResolver
                )().merge()
            }().mapException {
                ExtensionLoadException(ext, it) {
                    ext asContext "Extension"
                }
            }.merge()
        }
    }().merge()

    // Get all extension nodes in order
    val extensions = extensionNodes.flatMap(::allExtensions)

    // Get extension observer (if there is one after tweaker application) and observer each node
    environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }

    // Call init on all extensions, this is ordered correctly
    extensions.forEach {
        environment[ExtensionRunner].extract().init(it)().merge()
    }
}

private fun <T> Result<T>.handleStructuredException(
    env: ExtensionEnvironment
) {
    exceptionOrNull()?.run {
        if (this !is StructuredException) {
            throw this
        } else {
            handleException(env[exceptionCxtSerializersAttrKey].extract(), env[StackTracePrinter].extract(), this)
            exitProcess(-1)
        }
    }
}