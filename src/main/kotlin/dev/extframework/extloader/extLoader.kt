package dev.extframework.extloader

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.common.util.filterDuplicatesBy
import dev.extframework.common.util.make
import dev.extframework.extloader.environment.registerBasicSerializers
import dev.extframework.extloader.environment.registerLoaders
import dev.extframework.extloader.exception.BasicExceptionPrinter
import dev.extframework.extloader.exception.handleException
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.ExtensionLoadException
import dev.extframework.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.environment.*
import dev.extframework.tooling.api.exception.StackTracePrinter
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.*
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.target.ApplicationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.writeBytes
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
        set(ValueAttribute(workingDir, wrkDirAttrKey))
        set(ArchiveGraphAttribute(archiveGraph))
        set(DependencyTypeContainerAttribute(dependencyTypes))
        set(applicationTarget)
        set(extensionResolver)
    }

    public constructor(
        workingDir: Path,
        archiveGraph: ArchiveGraph,
        dependencyTypes: DependencyTypeContainer,
        applicationTarget: ApplicationTarget,
    ) : this() {
        set(ValueAttribute(workingDir, wrkDirAttrKey))
        set(ArchiveGraphAttribute(archiveGraph))
        set(DependencyTypeContainerAttribute(dependencyTypes))
        set(applicationTarget)
        set(DefaultExtensionResolver(ClassLoader.getSystemClassLoader(), this))
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

    environment.setUnless(object : ExtensionClassLoaderProvider {})

    environment.setUnless(MutableObjectContainerAttribute(partitionLoadersAttrKey))
    environment[partitionLoadersAttrKey].extract().registerLoaders()

    environment.setUnless(MutableObjectSetAttribute(exceptionCxtSerializersAttrKey).apply {
        registerBasicSerializers()
    })
    environment.setUnless(BasicExceptionPrinter())

    val uberExtension = generateUberExtension(extensionRequests)

    initExtension(environment, uberExtension.first, uberExtension.second)().handleStructuredException(environment)
}

private fun generateUberExtension(
    extensionRequests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,
): Pair<ExtensionDescriptor, ExtensionRepositorySettings> {
    val erm = ExtensionRuntimeModel(
        TOOLING_API_VERSION,
        "dev.extframework.extension",
        "uber",
        UUID.randomUUID().toString(),
        extensionRequests.map { (_, repo) ->
            mapOf(
                "location" to repo.layout.location,
                "type" to if (repo.layout is SimpleMavenDefaultLayout) "default" else "local",
            )
        },
        extensionRequests.mapTo(HashSet()) { (desc) ->
            ExtensionParent(
                desc.group,
                desc.artifact,
                desc.version,
            )
        }, setOf()
    )

    val dir = Files.createTempDirectory("uber-extension")
    val ermPath = dir
        .resolve("dev")
        .resolve("extframework")
        .resolve("extension")
        .resolve(erm.name)
        .resolve(erm.version)
        .resolve("${erm.name}-${erm.version}-erm.json")

    ermPath.make()
    ermPath.writeBytes(jacksonObjectMapper().writeValueAsBytes(erm))

    return erm.descriptor to ExtensionRepositorySettings.local(
        path = dir.toString()
    )
}

private fun initExtension(
    environment: InternalExtensionEnvironment,
    descriptor: ExtensionDescriptor,
    repository: ExtensionRepositorySettings,
) = job {
    fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
        return node.access.targets.map { it.relationship.node }
            .filterIsInstance<ExtensionNode>()
            .flatMapTo(HashSet(), ::allExtensions) + node
    }

    val extensionResolver = environment.extensionResolver

    fun loadTweakers(
        artifact: Artifact<ExtensionArtifactMetadata>
    ): Job<List<ExtensionPartitionContainer<TweakerPartitionNode, *>>> = job {
        val tweakerContainer: ExtensionPartitionContainer<TweakerPartitionNode, *>? = environment.archiveGraph
            .nodes()
            .filterIsInstance<ExtensionPartitionContainer<*, *>>()
            .find {
                it.descriptor.extension.group == artifact.metadata.descriptor.group
                        && it.descriptor.extension.artifact == artifact.metadata.descriptor.artifact
                        && it.descriptor.partition == TweakerPartitionLoader.TYPE
            } as? ExtensionPartitionContainer<TweakerPartitionNode, *> ?: run {
            val descriptor = PartitionDescriptor(artifact.metadata.descriptor, TweakerPartitionLoader.TYPE)

            val cacheResult = environment.archiveGraph.cache(
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

        val parents =
            artifact.parents.map {
                loadTweakers(it)().merge()
            }

        parents.flatten() + listOfNotNull(tweakerContainer)
    }

    val tweakers = job(JobName("Load tweakers")) {
        val artifact = extensionResolver.createContext(repository)
            .getAndResolve(
                ExtensionArtifactRequest(descriptor),
            )().merge()

        loadTweakers(artifact)().merge()
    }.mapException {
        ExtensionLoadException(descriptor, it)
    }().merge().filterDuplicatesBy { it.descriptor }

    tweakers.map { it.node }.forEach {
        it.tweaker.tweak(environment)().merge()
    }

    val extensionNodes = job(JobName("Load extensions")) {
        environment.archiveGraph.cache(
            ExtensionArtifactRequest(
                descriptor,
            ),
            repository,
            extensionResolver
        )().merge()

        environment.archiveGraph.get(
            descriptor,
            extensionResolver
        )().merge()
    }.mapException {
        ExtensionLoadException(descriptor, it)
    }().merge()

    // Get all extension nodes in order
    val extensions = extensionNodes
        .let(::allExtensions)
        .filterDuplicatesBy { it.descriptor }

    // Get extension observer (if there is one after tweaker application) and observer each node
    environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }

    // Call init on all extensions, this is ordered correctly
    extensions.forEach {
        environment[ExtensionRunner].getOrNull()?.init(it)?.invoke()?.merge()
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