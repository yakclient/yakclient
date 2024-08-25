//package dev.extframework.components.extloader.workflow
//
//import com.durganmcbroom.artifact.resolver.Artifact
//import com.durganmcbroom.artifact.resolver.ArtifactMetadata
//import com.durganmcbroom.jobs.Job
//import com.durganmcbroom.jobs.JobName
//import com.durganmcbroom.jobs.async.AsyncJob
//import com.durganmcbroom.jobs.async.asyncJob
//import com.durganmcbroom.jobs.async.mapAsync
//import com.durganmcbroom.jobs.job
//import com.durganmcbroom.jobs.mapException
//import com.durganmcbroom.resources.toResource
//import dev.extframework.archives.ArchiveReference
//import dev.extframework.archives.zip.classLoaderToArchive
//import dev.extframework.boot.archive.*
//import dev.extframework.boot.loader.ClassProvider
//import dev.extframework.boot.loader.ResourceProvider
//import dev.extframework.boot.loader.SourceDefiner
//import dev.extframework.boot.loader.SourceProvider
//import dev.extframework.boot.monad.Tagged
//import dev.extframework.boot.monad.Tree
//import dev.extframework.common.util.LazyMap
//import dev.extframework.common.util.children
//import dev.extframework.components.extloader.environment.CommonEnvironment
//import dev.extframework.components.extloader.extension.DefaultExtensionResolver
//import dev.extframework.components.extloader.extension.ExtensionLoadException
//import dev.extframework.components.extloader.extension.artifact.ExtensionRepositoryFactory
//import dev.extframework.components.extloader.extension.partition.DefaultPartitionResolver
//import dev.extframework.components.extloader.extension.partition.TweakerPartitionLoader
//import dev.extframework.components.extloader.extension.partition.TweakerPartitionNode
//import dev.extframework.components.extloader.util.emptyArchiveReference
//import dev.extframework.internal.api.environment.*
//import dev.extframework.internal.api.extension.*
//import dev.extframework.internal.api.extension.artifact.ExtensionArtifactMetadata
//import dev.extframework.internal.api.extension.artifact.ExtensionArtifactRequest
//import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
//import dev.extframework.internal.api.extension.partition.ExtensionPartitionContainer
//import dev.extframework.internal.api.extension.partition.ExtensionPartitionMetadata
//import dev.extframework.internal.api.extension.partition.PartitionAccessTree
//import dev.extframework.internal.api.extension.partition.PartitionLoaderHelper
//import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactMetadata
//import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest
//import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
//import dev.extframework.internal.api.extension.partition.artifact.partitionNamed
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.awaitAll
//import kotlinx.coroutines.runBlocking
//import org.objectweb.asm.ClassReader
//import org.objectweb.asm.tree.ClassNode
//import java.nio.file.Path
//import kotlin.io.path.isDirectory
//
//public data class TestWorkflowContext(
//    val extensions: List<Pair<ExtensionRuntimeModel, List<PartitionRuntimeModel>>>,
//) : WorkflowContext
//
//public class TestWorkflow : Workflow<TestWorkflowContext> {
//    override val name: String = "test-workflow"
//
//    override fun work(context: TestWorkflowContext, environment: ExtensionEnvironment): Job<Unit> {
//        // Create initial environment
//        environment += CommonEnvironment(environment[WorkingDirectoryAttribute].extract().path)
//
//        fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
//            return node.access.targets.map { it.relationship.node }
//                .filterIsInstance<ExtensionNode>()
//                .flatMapTo(HashSet(), ::allExtensions) + node
//        }
//
//        environment += TestExtensionResolver(
//            environment[ParentClassloaderAttribute].extract().cl,
//            environment,
//        )
//
//        // Get extension resolver
//        val extensionResolver = environment[ExtensionResolver].extract()
//
//        fun loadTweakers(
//            artifact: List<
//                    Pair<PartitionRuntimeModel, ExtensionRuntimeModel>,
//        ): AsyncJob<List<ExtensionPartitionContainer<TweakerPartitionNode, *>>> = asyncJob {
//            val parents =
//
//            val tweakerContainer: ExtensionPartitionContainer<TweakerPartitionNode, *>? = run {
//                val descriptor = PartitionDescriptor(artifact.metadata.descriptor, TweakerPartitionLoader.TYPE)
//
//                val cacheResult = environment.archiveGraph.cacheAsync(
//                    PartitionArtifactRequest(descriptor),
//                    artifact.metadata.repository,
//                    extensionResolver.partitionResolver,
//                )()
//                if (cacheResult.isFailure && cacheResult.exceptionOrNull() is ArchiveException.ArchiveNotFound) return@run null
//                else cacheResult.merge()
//
//                environment.archiveGraph.get(
//                    descriptor,
//                    extensionResolver.partitionResolver,
//                )().merge()
//            } as? ExtensionPartitionContainer<TweakerPartitionNode, *>
//
//            parents.awaitAll().flatten() + listOfNotNull(tweakerContainer)
//        }
//
//        val tweakers = job(JobName("Load tweakers")) {
//            runBlocking(Dispatchers.IO) {
//                val artifact = extensionResolver.createContext(context.repository)
//                    .getAndResolveAsync(
//                        ExtensionArtifactRequest(context.extension)
//                    )().merge()
//
//                loadTweakers(artifact)().merge()
//            }
//        }().merge()
//
//        tweakers.map { it.node }.forEach {
//            it.tweaker.tweak(environment)().merge()
//        }
//
//        val extensionNode = job(JobName("Load extensions")) {
//            environment.archiveGraph.cache(
//                ExtensionArtifactRequest(
//                    context.extension,
//                ),
//                context.repository,
//                extensionResolver
//            )().merge()
//            environment.archiveGraph.get(
//                context.extension,
//                extensionResolver
//            )().merge()
//        }().mapException {
//            ExtensionLoadException(context.extension, it) {
//                context.extension asContext "Extension"
//                this@DevWorkflow.name asContext "Workflow/Environment"
//            }
//        }.merge()
//
//        // Get all extension nodes in order
//        val extensions = allExtensions(extensionNode)
//
//        // Get extension observer (if there is one after tweaker application) and observer each node
//        environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }
//
//        // Call init on all extensions, this is ordered correctly
//        extensions.forEach(environment[ExtensionRunner].extract()::init)
//
//    }
//}
//
//private class TestExtensionResolver(
//    environment: ExtensionEnvironment,
//) : DefaultExtensionResolver(ClassLoader.getSystemClassLoader(), environment) {
//    override val partitionResolver: DefaultPartitionResolver
//        get() = super.partitionResolver
//}
//
//private class TestPartitionResolver(
//    extensionRepositoryFactory: ExtensionRepositoryFactory,
//    environment: ExtensionEnvironment,
//
//    val extensions: List<Pair<ExtensionRuntimeModel, List<PartitionRuntimeModel>>>
//) : DefaultPartitionResolver(extensionRepositoryFactory, environment, {
//    run {
//        val map = LazyMap<ExtensionDescriptor, ExtensionClassLoader> {
//            ExtensionClassLoader(
//                it.name,
//                ArrayList(),
//                ClassLoader.getSystemClassLoader(),
//            )
//        }
//
//        map[it]!!
//    }
//}) {
//    private fun createArchiveRefFromClassPath(): ArchiveReference {
//        val rootPath = Path.of(ClassLoader.getSystemResource("")!!.path)
//
//        val empty = emptyArchiveReference(rootPath.toUri())
//
//        fun createEntries(path: Path) {
//            path.children().forEach {
//                if (it.isDirectory()) createEntries(it)
//                else {
//                    val entry = ArchiveReference.Entry(
//                        it.toString().removePrefix(rootPath.toString()),
//                        it.toResource(),
//                        false,
//                        empty
//                    )
//
//                    empty.writer.put(entry)
//                }
//            }
//        }
//
//        createEntries(rootPath)
//
//        return empty
//    }
//
//    private fun createMetadata(
//        erm: ExtensionRuntimeModel,
//        prm: PartitionRuntimeModel,
//    ) = object {
//        operator fun component1() = createArchiveRefFromClassPath()
//        operator fun component2() = getLoader(prm)
//        operator fun component3() = parseMetadata(component2(), prm, erm, component1())
//    }
//
//    override fun load(
//        data: ArchiveData<PartitionDescriptor, CachedArchiveResource>,
//        accessTree: ArchiveAccessTree,
//        helper: ResolutionHelper
//    ): Job<ExtensionPartitionContainer<*, *>> = job {
//        val (erm, partitions) = extensions.find {
//            it.first.descriptor == data.descriptor.extension
//        } ?: throw IllegalArgumentException("Please provide the extension: '${data.descriptor.extension}' in the test.")
//
//        val prm = partitions.find { it.name == data.descriptor.partition }
//            ?: throw IllegalArgumentException("Please provide the partition: '${data.descriptor.partition}' in extension: '${data.descriptor.extension}' the test.")
//
//        val (archive, loader, metadata) = createMetadata(erm, prm)
//
//        loader.load(
//            metadata().merge(),
//            archive,
//            object : PartitionAccessTree {
//                override val partitions: List<ExtensionPartitionContainer<*, *>> =
//                    accessTree.targets.map { it.relationship.node }
//                        .filterIsInstance<ExtensionPartitionContainer<*, *>>()
//
//                override val descriptor: ArtifactMetadata.Descriptor = data.descriptor
//                override val targets: List<ArchiveTarget> = accessTree.targets
//            },
//            object : PartitionLoaderHelper {
//                override val parentClassLoader: ClassLoader = ClassLoader.getSystemClassLoader()
//                override val erm: ExtensionRuntimeModel = erm
//
//                override fun metadataFor(reference: PartitionModelReference): AsyncJob<ExtensionPartitionMetadata> =
//                    asyncJob {
//                        val prm2 = partitions.find { it.name == reference.name }
//                            ?: throw IllegalArgumentException("Please provide the partition: '${data.descriptor.partition}' in extension: '${data.descriptor.extension}' the test.")
//
//                        createMetadata(
//                            erm, prm2
//                        ).component3()().merge()
//                    }
//
//                override fun get(name: String): CachedArchiveResource? {
//                    return data.resources[name]
//                }
//
//                override fun newClassLoader(): ClassLoader {
//                    return this.javaClass.classLoader
//                }
//
//                override fun newClassLoader(
//                    classProvider: ClassProvider,
//                    resourceProvider: ResourceProvider,
//                    sourceProvider: SourceProvider,
//                    sourceDefiner: SourceDefiner
//                ): ClassLoader {
//                    TODO("Not yet implemented")
//                }
//            }
//        )().merge().also {
//            parentLoader.partitions.add(it)
//        }
//
//    }
//
//    override fun cache(
//        artifact: Artifact<PartitionArtifactMetadata>,
//        helper: CacheHelper<PartitionDescriptor>
//    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
//        helper.newData(artifact.metadata.descriptor, listOf())
//    }
//}