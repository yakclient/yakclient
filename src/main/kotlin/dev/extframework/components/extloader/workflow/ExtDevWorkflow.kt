package dev.extframework.components.extloader.workflow

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.archive.mapper.findShortest
import dev.extframework.archive.mapper.newMappingsGraph
import dev.extframework.archive.mapper.transform.transformArchive
import dev.extframework.archives.ArchiveFinder
import dev.extframework.archives.ArchiveTree
import dev.extframework.archives.Archives
import dev.extframework.archives.mixin.MixinInjection
import dev.extframework.boot.component.context.ContextNodeValue
import dev.extframework.boot.loader.*
import dev.extframework.common.util.immutableLateInit
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.api.environment.*
import dev.extframework.components.extloader.api.exception.StructuredException
import dev.extframework.components.extloader.api.extension.ExtensionNodeObserver
import dev.extframework.components.extloader.api.extension.ExtensionRunner
import dev.extframework.components.extloader.api.target.ApplicationParentClProvider
import dev.extframework.components.extloader.api.target.ApplicationTarget
import dev.extframework.components.extloader.environment.ExtensionDevEnvironment
import dev.extframework.components.extloader.exception.ExtLoaderExceptions
import dev.extframework.components.extloader.extension.ExtensionLoadException
import dev.extframework.components.extloader.extension.ExtensionNode
import dev.extframework.components.extloader.extension.ExtensionResolver
import dev.extframework.components.extloader.extension.artifact.ExtensionArtifactRequest
import dev.extframework.components.extloader.extension.artifact.ExtensionDescriptor
import dev.extframework.components.extloader.extension.artifact.ExtensionRepositorySettings
import dev.extframework.components.extloader.extension.mapping.MojangExtensionMappingProvider
import dev.extframework.components.extloader.extension.partition.TweakerPartitionMetadata
import dev.extframework.components.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.components.extloader.target.TargetLinker
import dev.extframework.minecraft.bootstrapper.MinecraftClassTransformer
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

internal data class ExtDevWorkflowContext(
    val extension: ExtensionDescriptor, val repository: ExtensionRepositorySettings, val mappingType: String
) : ExtLoaderWorkflowContext

internal class ExtDevWorkflow : ExtLoaderWorkflow<ExtDevWorkflowContext> {
    override val name: String = "extension-dev"

    override fun parse(node: ContextNodeValue): ExtDevWorkflowContext {
        val extensionTree = node.coerceTree()["extension"]?.coerceTree().check { "environment.extension" }
        val extDescriptor =
            extensionTree["descriptor"].check { "environment.extension.descriptor" }.coerceTree().parseDescriptor()
        val extRepository =
            extensionTree["repository"].check { "environment.extension.repository" }.coerceTree().parseSettings()
        val mappingsType = node.coerceTree().getCoerceCheckString("mappingType")

        return ExtDevWorkflowContext(
            extDescriptor, extRepository, mappingsType
        )
    }

    override fun work(
        context: ExtDevWorkflowContext, environment: ExtLoaderEnvironment, args: Array<String>
    ): Job<Unit> = job(JobName("Run extension dev workflow")) {
        // Create initial environment
        environment += ExtensionDevEnvironment(environment[WorkingDirectoryAttribute].extract().path)
        environment += ApplicationMappingTarget(
            context.mappingType
        )
        // Add dev graph to environment
        environment += DevExtensionResolver(
            Archives.Finders.ZIP_FINDER,
            environment[ParentClassloaderAttribute].extract().cl,
            environment,
        )

        val appTarget = environment[ApplicationTarget].extract()
        val appReference = appTarget.reference

        // Delete extension, we want to re-cache the erm since we are in dev mode
        Files.deleteIfExists(
            environment.archiveGraph.path resolve Path.of(
                context.extension.group.replace('.', File.separatorChar),
                context.extension.artifact,
                context.extension.version,
                "${context.extension.artifact}-${context.extension.version}-archive-metadata.json"
            )
        )
        Files.deleteIfExists(
            environment.archiveGraph.path resolve "tweakers" resolve Path.of(
                context.extension.group.replace('.', File.separatorChar),
                context.extension.artifact,
                context.extension.version,
                "${context.extension.artifact}-${context.extension.version}-archive-metadata.json"
            )
        )

        fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
            return node.parents.flatMapTo(HashSet(), ::allExtensions) + node
        }

        // Get extension resolver
        val extensionResolver = environment[ExtensionResolver].extract()

        // Load a cacher and attempt to cache the extension request
        val extensionNode = job(JobName("Load extensions")) {
            environment.archiveGraph.cache(
                ExtensionArtifactRequest(
                    context.extension, includeScopes = setOf("compile", "runtime", "import")
                ),
                context.repository,
                extensionResolver
            )().merge()
            environment.archiveGraph.get(
                context.extension,
                extensionResolver
            )().merge()
        }().mapException {
            ExtensionLoadException(context.extension, it) {
                context.extension asContext "Extension"
                this@ExtDevWorkflow.name asContext "Workflow/Environment"
            }
        }.merge()

        val tweakers = allExtensions(extensionNode).flatMap(ExtensionNode::partitions).filter {
            it.metadata is TweakerPartitionMetadata
        }.map {
            it.node
        }.filterIsInstance<TweakerPartitionNode>()
        tweakers.forEach {
            it.tweaker.tweak(environment)().merge()
        }

        val mcVersion by appReference.descriptor::version
        val mapperObjects = environment[MutableObjectSetAttribute.Key<MappingsProvider>("mapping-providers")].extract()
        val mappingGraph = newMappingsGraph(mapperObjects)

        transformArchive(
            appReference.reference, appReference.dependencyReferences,
            mappingGraph.findShortest(
                MojangExtensionMappingProvider.FAKE_TYPE, context.mappingType
            ).forIdentifier(mcVersion),
            MojangExtensionMappingProvider.FAKE_TYPE, context.mappingType,
        )

        // Get all extension nodes in order
        val extensions = allExtensions(extensionNode)

        // Get extension observer (if there is one after tweaker application) and observer each node
        environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }

        extensions.forEach {
            it.container?.injectMixins { metadata ->
                appTarget.mixin(metadata.destination, object : MinecraftClassTransformer {
                    override val trees: List<ArchiveTree> = listOf()

                    override fun transform(node: ClassNode): ClassNode {
                        val transformer =
                            (metadata.injection as MixinInjection<MixinInjection.InjectionData>).apply(metadata.data)

                        transformer.ct(node)
                        node.methods.forEach(transformer.mt::invoke)
                        node.fields.forEach(transformer.ft::invoke)

                        return node
                    }
                })
            }?.invoke()?.merge()
        }

        var targetClassProvider: ClassProvider by immutableLateInit()
        var targetResourceProvider: ResourceProvider by immutableLateInit()

        // Create linker with delegating to the uninitialized class providers
        val linker = TargetLinker(
            targetDescriptor = appReference.descriptor,
            target = object : ClassProvider {
                override val packages: Set<String> by lazy { targetClassProvider.packages }

                override fun findClass(name: String): Class<*>? = targetClassProvider.findClass(name)
            },
            targetResources = object : ResourceProvider {
                override fun findResources(name: String): Sequence<URL> {
                    return targetResourceProvider.findResources(name)
                }
            },
        )
        environment += linker

        // Create the minecraft parent classloader with the delegating linker
        val parentLoader = environment[ApplicationParentClProvider].extract().getParent(linker, environment)

        // Init minecraft
        appTarget.reference.load(parentLoader)().merge()

        // Initialize the first clas provider to allow extensions access to minecraft
        targetClassProvider =
            DelegatingClassProvider((appReference.dependencyHandles + appReference.handle).map(::ArchiveClassProvider))
        targetResourceProvider =
            DelegatingResourceProvider((appReference.dependencyHandles + appReference.handle).map(::ArchiveResourceProvider))

        // Setup extensions, dont init yet
        extensions.forEach { n ->
            val container = n.container
            container?.setup(linker)?.invoke()?.mapException {
                StructuredException(
                    ExtLoaderExceptions.ExtensionSetupException,
                    it
                ) {
                    n.erm.name asContext "Extension name"
                }
            }?.merge()

            n.partitions.forEach {
                linker.addMiscClasses(ArchiveClassProvider(it.node.archive))
            }
            linker.addMiscResources(object : ResourceProvider {
                override fun findResources(name: String): Sequence<URL> {
                    return n.archive?.classloader?.getResource(name)?.let { sequenceOf(it) } ?: emptySequence()
                }
            })
        }

        // Specifically NOT adding tweaker resources.
        tweakers.forEach {
            it.archive.let { a -> linker.addMiscClasses(
                ArchiveClassProvider(a)
            ) }
        }

        // Call init on all extensions, this is ordered correctly
        extensions.forEach(environment[ExtensionRunner].extract()::init)

        // Start minecraft
        appTarget.start(args)
    }
}

private class DevExtensionResolver(
    finder: ArchiveFinder<*>, parent: ClassLoader, environment: ExtLoaderEnvironment
) : ExtensionResolver(
    finder, parent, environment
)