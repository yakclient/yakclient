package dev.extframework.extloader

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveRelationship
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.monad.toList
import dev.extframework.common.util.filterDuplicates
import dev.extframework.extloader.environment.registerBasicSerializers
import dev.extframework.extloader.environment.registerLoaders
import dev.extframework.extloader.exception.BasicExceptionPrinter
import dev.extframework.extloader.exception.handleException
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.extloader.uber.*
import dev.extframework.tooling.api.environment.*
import dev.extframework.tooling.api.exception.StackTracePrinter
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.*
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import java.nio.file.Path
import kotlin.system.exitProcess

public class InternalExtensionEnvironment private constructor() : ExtensionEnvironment() {
    public val workingDir: Path
        get() = get(wrkDirAttrKey).extract().value
    public val archiveGraph: ArchiveGraph
        get() = get(ArchiveGraphAttribute).extract().graph
    public val dependencyTypes: DependencyTypeContainer
        get() = get(dependencyTypesAttrKey).extract().container

    //    public val application: ApplicationTarget by get(ApplicationTarget)
    public val extensionResolver: ExtensionResolver by get(ExtensionResolver)

    public constructor(
        workingDir: Path,
        archiveGraph: ArchiveGraph,
        dependencyTypes: DependencyTypeContainer,
//        applicationTarget: ApplicationTarget,
        extensionResolver: ExtensionResolver,
    ) : this() {
        set(ValueAttribute(workingDir, wrkDirAttrKey))
        set(ArchiveGraphAttribute(archiveGraph))
        set(DependencyTypeContainerAttribute(dependencyTypes))
//        set(applicationTarget)
        set(extensionResolver)
    }

    public constructor(
        workingDir: Path,
        archiveGraph: ArchiveGraph,
        dependencyTypes: DependencyTypeContainer,
//        applicationTarget: ApplicationTarget,
    ) : this() {
        set(ValueAttribute(workingDir, wrkDirAttrKey))
        set(ArchiveGraphAttribute(archiveGraph))
        set(DependencyTypeContainerAttribute(dependencyTypes))
//        set(applicationTarget)
        set(DefaultExtensionResolver(ClassLoader.getSystemClassLoader(), this))
    }
}

public fun initExtensions(
    extensionRequests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,

    environment: InternalExtensionEnvironment,
): AsyncJob<Unit> = asyncJob() {
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

    tweakEnvironment(
        environment,
        extensionRequests.entries.map { it.key to it.value },
    )().handleStructuredException(environment)
}

//private fun generateUberExtension(
//    extensionRequests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,
//): Pair<ExtensionDescriptor, ExtensionRepositorySettings> {
//    val erm = ExtensionRuntimeModel(
//        TOOLING_API_VERSION,
//        "dev.extframework.extension",
//        "uber",
//        UUID.randomUUID().toString(),
//        extensionRequests.map { (_, repo) ->
//            mapOf(
//                "location" to repo.layout.location,
//                "type" to if (repo.layout is SimpleMavenDefaultLayout) "default" else "local",
//            )
//        },
//        extensionRequests.mapTo(HashSet()) { (desc) ->
//            ExtensionParent(
//                desc.group,
//                desc.artifact,
//                desc.version,
//            )
//        }, setOf()
//    )
//
//    val dir = Files.createTempDirectory("uber-extension")
//    val ermPath = dir
//        .resolve("dev")
//        .resolve("extframework")
//        .resolve("extension")
//        .resolve(erm.name)
//        .resolve(erm.version)
//        .resolve("${erm.name}-${erm.version}-erm.json")
//
//    ermPath.make()
//    ermPath.writeBytes(jacksonObjectMapper().writeValueAsBytes(erm))
//
//    return erm.descriptor to ExtensionRepositorySettings.local(
//        path = dir.toString()
//    )
//}


private fun tweakEnvironment(
    environment: InternalExtensionEnvironment,
    requests: List<Pair<ExtensionDescriptor, ExtensionRepositorySettings>>
//    descriptor: ExtensionDescriptor,
//    repository: ExtensionRepositorySettings,
) = asyncJob {
    val extensionResolver = environment.extensionResolver

    val uberExtensionDescriptor = UberDescriptor("Extension tree")
    val uberExtensionRequest = UberArtifactRequest(
        uberExtensionDescriptor,
        requests.map {
            UberParentRequest(
                ExtensionArtifactRequest(it.first), it.second, extensionResolver
            )
        }.filterDuplicates()
    )

    environment.archiveGraph.cacheAsync(
        uberExtensionRequest,
        UberRepositorySettings,
        UberResolver
    )().merge()
    //.map { tagged ->
//        tagged.value
//    }//.toList().filter { archive -> archive.descriptor is ExtensionDescriptor }

    val extensionTree = environment.archiveGraph.get(
        uberExtensionDescriptor,
        UberResolver
    )().merge()
        .buildTree()
        .toList()//.filter { archive -> archive.descriptor is ExtensionDescriptor }
        .filterIsInstance<ExtensionNode>()

    val uberTweakerParents = extensionTree
        .filter { archive ->
            val erm = extensionResolver.accessBridge.ermFor(archive.descriptor)
            erm.partitions.any { model -> model.name == "tweaker" }
        }
        // TODO reason for this? They will just get cached by the uber resolver later.
        .onEach { archive ->
            environment.archiveGraph.cacheAsync(
                PartitionArtifactRequest(
                    archive.descriptor,
                    "tweaker",
                ),
                extensionResolver.accessBridge.repositoryFor(archive.descriptor),
                extensionResolver.partitionResolver
            )().merge()
        }
        .map { archive ->
            UberParentRequest(
                PartitionArtifactRequest(
                    archive.descriptor,
                    "tweaker",
                ),
                extensionResolver.accessBridge.repositoryFor(archive.descriptor),
                extensionResolver.partitionResolver
            )
        }

    val uberDescriptor = UberDescriptor("All Tweakers")
    val uberTweakerRequest = UberArtifactRequest(
        uberDescriptor,
        uberTweakerParents,
    )

    environment.archiveGraph.cacheAsync(
        uberTweakerRequest,
        UberRepositorySettings,
        UberResolver
    )().merge()

    val uberTweakers = environment.archiveGraph.get(
        uberDescriptor,
        UberResolver
    )().merge()

    val tweakers = extensionTree
        .mapNotNull { archive ->
            uberTweakers.access
                .targets
                .map { target -> target.relationship.node }
                .filterIsInstance<ExtensionPartitionContainer<TweakerPartitionNode, *>>()
                .find { container -> container.descriptor.extension == archive.descriptor }
        }
        .reversed()
        .filterDuplicates()

    tweakers.map { it.node }.forEach {
        it.tweaker.tweak(environment)().merge()
    }

    // Get all extension nodes in order
    val extensions = extensionTree
        .toSet()
        .map { environment.archiveGraph.getNode(it.descriptor)!! as ExtensionNode }
        .reversed()

    // Both of these are just hooks that given end developers more control over how the extension
    // loading process works. If the end goal is just creating a tweaked environment they should
    // not be used

    // Provides predefined actions for pre init do to
    val preInitActions = object : ExtensionPreInitializer.Actions {
        val parents = ArrayList<UberParentRequest<*, *, *>>()

        override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, S : RepositorySettings> addRequest(
            request: T,
            repository: S,
            resolver: ArchiveNodeResolver<D, T, *, S, *>
        ) {
            parents.add(UberParentRequest(request, repository, resolver))
        }
    }

    // Call pre init, should do things like load additional partitions
    extensions.forEach {
        environment[ExtensionPreInitializer].getOrNull()?.preInit(it, preInitActions)?.invoke()?.merge()
    }

    if (preInitActions.parents.isNotEmpty()) {
        val req = UberArtifactRequest("Extension Pre-init", preInitActions.parents)

        environment.archiveGraph.cacheAsync(
            req,
            UberRepositorySettings,
            UberResolver
        )().merge()

        environment.archiveGraph.getAsync(
            req.descriptor,
            UberResolver
        )().merge()
    }

    // Call init this does the actual initialization
    extensions.forEach {
        environment[ExtensionInitializer].getOrNull()?.init(it)?.invoke()?.merge()
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

private fun ArchiveNode<*>.buildTree(): Tree<ArchiveNode<*>> {
    val parents = access.targets
        .filter { it.relationship is ArchiveRelationship.Direct }
        .map { it.relationship.node }

    return Tree(
        this,
        parents.map {
            it.buildTree()
        }
    )
}