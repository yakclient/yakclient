package dev.extframework.extloader.workflow

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings

public data class ProdWorkflowContext(
    val extensions: Map<ExtensionDescriptor, ExtensionRepositorySettings>
) : WorkflowContext

public class ProdWorkflow : Workflow<ProdWorkflowContext> {
    override val name: String = "production"

    override fun work(context: ProdWorkflowContext, environment: ExtensionEnvironment): Job<Unit> = job {
//        environment += CommonEnvironment(environment[WorkingDirectoryAttribute].extract().path)
//        environment += ApplicationMappingTarget(MojangExtensionMappingProvider.OBFUSCATED)
//
//        // Add dev graph to environment
//        environment += ExtensionResolver(
//            Archives.Finders.ZIP_FINDER,
//            environment[ParentClassloaderAttribute].extract().cl,
//            environment,
//        )
//
//        val appTarget = environment[ApplicationTarget].extract()
//
//        fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
//            return node.parents.flatMapTo(HashSet(), ::allExtensions) + node
//        }
//
//        // Get extension resolver
//        val extensionResolver = environment[ExtensionResolver].extract()
//
//        // Load a cacher and attempt to cache the extension request
//        val extensionNodes = job(JobName("Load extensions")) {
//            context.extensions.map { (extension, repository) ->
//                job(JobName("Load extension: '$extension'")) {
//                    environment.archiveGraph.cache(
//                        ExtensionArtifactRequest(
//                            extension, includeScopes = setOf("compile", "runtime", "import")
//                        ),
//                        repository,
//                        extensionResolver
//                    )().merge()
//                    environment.archiveGraph.get(
//                        extension,
//                        extensionResolver
//                    )().merge()
//                }().mapException {
//                    ExtensionLoadException(extension, it) {
//                        extension asContext "Extension"
//                        this@ProdWorkflow.name asContext "Workflow/Environment"
//                    }
//                }.merge()
//            }
//        }().merge()
//
//        val extensions = extensionNodes
//            .asSequence()
//            .flatMap { allExtensions(it) }
//
//
//        val tweakers = extensions
//            .flatMap(ExtensionNode::partitions)
//            .filter { it.metadata is TweakerPartitionMetadata }
//            .map(ExtensionPartitionContainer<*, *>::node)
//            .filterIsInstance<TweakerPartitionNode>().toList()
//
//        tweakers.forEach {
//            it.tweaker.tweak(environment)().merge()
//        }
//
//        // TODO duplicate
//        // Get extension observer (if there is one after tweaker application) and observer each node
//        environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }
//
//        val appHandle = classLoaderToArchive(appTarget.classloader)
//
//        extensions.forEach {
//            it.container?.injectMixins { metadata ->
//                appTarget.addTransformer(metadata.destination) { node ->
//                    val transformer =
//                        (metadata.injection as MixinInjection<MixinInjection.InjectionData>).apply(metadata.data)
//
//                    transformer.ct(node)
//                    node.methods.forEach(transformer.mt::invoke)
//                    node.fields.forEach(transformer.ft::invoke)
//
//                    AwareClassWriter(
//                        handles = listOf(appHandle),
//                        flags = ClassWriter.COMPUTE_MAXS
//                    ).also(node::accept).toByteArray()
//                }
//            }?.invoke()?.merge()
//        }
//
//        var targetClassProvider: ClassProvider by immutableLateInit()
//        var targetResourceProvider: ResourceProvider by immutableLateInit()
//
//        // Create linker with delegating to the uninitialized class providers
//        val linker = TargetLinker(
//            targetDescriptor = appTarget.descriptor,
//            target = object : ClassProvider {
//                override val packages: Set<String> by lazy { targetClassProvider.packages }
//
//                override fun findClass(name: String): Class<*>? = targetClassProvider.findClass(name)
//            },
//            targetResources = object : ResourceProvider {
//                override fun findResources(name: String): Sequence<URL> {
//                    return targetResourceProvider.findResources(name)
//                }
//            },
//        )
//        environment += linker
//
//        // Initialize the first clas provider to allow extensions access to minecraft
//        targetClassProvider = ArchiveClassProvider(appHandle)
//        targetResourceProvider = ArchiveResourceProvider(appHandle)
//
//        // Setup extensions, dont init yet
//        extensions.forEach { n ->
//            val container = n.container
//            container?.setup(linker)?.invoke()?.mapException {
//                StructuredException(
//                    ExtLoaderExceptions.ExtensionSetupException,
//                    it
//                ) {
//                    n.erm.name asContext "Extension name"
//                }
//            }?.merge()
//
//            n.partitions.forEach {
//                linker.addExtensionClasses(ArchiveClassProvider(it.node.archive))
//            }
//            linker.addExtensionResources(object : ResourceProvider {
//                override fun findResources(name: String): Sequence<URL> {
//                    return n.archive?.classloader?.getResource(name)?.let { sequenceOf(it) } ?: emptySequence()
//                }
//            })
//        }
//
//        // Specifically NOT adding tweaker resources.
//        tweakers.forEach {
//            it.archive.let { a ->
//                linker.addExtensionClasses(
//                    ArchiveClassProvider(a)
//                )
//            }
//        }
//
//        // Call init on all extensions, this is ordered correctly
//        extensions.forEach(environment[ExtensionRunner].extract()::init)
    }
}