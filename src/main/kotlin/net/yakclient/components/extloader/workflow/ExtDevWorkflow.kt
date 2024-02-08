package net.yakclient.components.extloader.workflow

import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.job
import net.yakclient.archive.mapper.transform.transformArchive
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.boot.loader.*
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.common.util.immutableLateInit
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionNodeObserver
import net.yakclient.components.extloader.api.extension.ExtensionRunner
import net.yakclient.components.extloader.environment.ExtensionDevEnvironment
import net.yakclient.components.extloader.extension.*
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactRequest
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositorySettings
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import net.yakclient.components.extloader.mapping.findShortest
import net.yakclient.components.extloader.mapping.newMappingsGraph
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.api.mapping.MappingsProvider
import net.yakclient.components.extloader.api.target.ApplicationParentClProvider
import net.yakclient.components.extloader.target.TargetLinker
import net.yakclient.components.extloader.tweaker.EnvironmentTweakerResolver
import net.yakclient.components.extloader.tweaker.EnvironmentTweakerNode
import net.yakclient.minecraft.bootstrapper.MinecraftClassTransformer
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

    override suspend fun work(
        context: ExtDevWorkflowContext, environment: ExtLoaderEnvironment, args: Array<String>
    ): JobResult<Unit, Throwable> = job(JobName("Run extension dev workflow")) {
        // Create initial environment
        environment += ExtensionDevEnvironment(environment[WorkingDirectoryAttribute]!!.path)
        environment += ApplicationMappingTarget(
            context.mappingType
        )
        // Add dev graph to environment
        environment += EnvironmentTweakerResolver(
            environment
        )
        environment += DevExtensionResolver(
            Archives.Finders.ZIP_FINDER,
            PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
            environment[ParentClassloaderAttribute]!!.cl,
            environment,
        )

        val appTarget = environment[ApplicationTarget]!!
        val appReference = appTarget.reference

        var targetClassProvider: ClassProvider by immutableLateInit()
        var targetResourceProvider: ResourceProvider by immutableLateInit()

        // Create linker with delegating to the uninitialized class providers
        val linker = TargetLinker(
//            environment
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

        // Delete extension, we want to re-cache since we are in dev mode
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

        fun EnvironmentTweakerNode.applyTweakers(env: ExtLoaderEnvironment) {
            parents.forEach { it.applyTweakers(env) }
            tweaker?.tweak(env)
        }

        val tweakerDesc = context.extension.copy(
            classifier = "tweaker"
        )
        val tweakerResolver = environment[EnvironmentTweakerResolver]!!
        val tweaker = if (environment.archiveGraph.cache(
                ExtensionArtifactRequest(tweakerDesc, includeScopes = setOf("compile", "runtime", "import")),
                context.repository,
                tweakerResolver
            ).wasSuccess()
        ) {
            environment.archiveGraph.get(
                tweakerDesc,
                tweakerResolver
            ).attempt()
        } else null
        tweaker?.applyTweakers(environment)

        fun allTweakers(node: EnvironmentTweakerNode): List<EnvironmentTweakerNode> {
            return listOf(node) + node.parents.flatMap { allTweakers(it) }
        }

        val allTweakers = tweaker?.let(::allTweakers) ?: listOf()

        val mcVersion by appReference.descriptor::version
        val mapperObjects = environment[MutableObjectSetAttribute.Key<MappingsProvider>("mapping-providers")]!!
        val mappingGraph = newMappingsGraph(mapperObjects)

        transformArchive(
            appReference.reference, appReference.dependencyReferences,
            mappingGraph.findShortest(
                MojangExtensionMappingProvider.FAKE_TYPE, context.mappingType
            ).forIdentifier(mcVersion),
            MojangExtensionMappingProvider.FAKE_TYPE, context.mappingType,
        )

        fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
            return node.parents.flatMapTo(HashSet(), ::allExtensions) + node
        }

        // Get extension resolver
        val extensionResolver = environment[ExtensionResolver]!!

        // Load a cacher and attempt to cache the extension request

        environment.archiveGraph.cache(
            ExtensionArtifactRequest(
                context.extension, includeScopes = setOf("compile", "runtime", "import")
            ),
            context.repository,
            extensionResolver
        ).attempt()
        val extensionNode = environment.archiveGraph.get(
            context.extension,
            extensionResolver
        ).attempt()

        // Get all extension nodes in order
        val extensions = allExtensions(extensionNode)

        // Get extension observer (if there is one after tweaker application) and observer each node
        environment[ExtensionNodeObserver]?.let { extensions.forEach(it::observe) }

        extensions.forEach {
            it.container?.injectMixins { to, metadata ->
                appTarget.mixin(to, object : MinecraftClassTransformer {
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
            }
        }

        // Create the minecraft parent classloader with the delegating linker
        val parentLoader = environment[ApplicationParentClProvider]!!.getParent(linker, environment)

        // Init minecraft
        appTarget.reference.load(parentLoader)

        // Initialize the first clas provider to allow extensions access to minecraft
        targetClassProvider =
            DelegatingClassProvider((appReference.dependencyHandles + appReference.handle).map(::ArchiveClassProvider))
        targetResourceProvider =
            DelegatingResourceProvider((appReference.dependencyHandles + appReference.handle).map(::ArchiveResourceProvider))

        // Setup extensions, dont init yet
        extensions.forEach {
            val container = it.container
            container?.setup(linker)

            it.archive?.let { a -> linker.addMiscClasses(ArchiveClassProvider(a)) }
            linker.addMiscResources(object : ResourceProvider {
                override fun findResources(name: String): Sequence<URL> {
                    return it.archive?.classloader?.getResource(name)?.let { sequenceOf(it) } ?: emptySequence()
                }
            })
        }

        // Specifically NOT adding tweaker resources.
        allTweakers.forEach {
            it.archive?.let { a -> linker.addMiscClasses(ArchiveClassProvider(a)) }
        }

        // Call init on all extensions, this is ordered correctly
        extensions.forEach(environment[ExtensionRunner]!!::init)

        // Start minecraft
        appTarget.start(args)
    }
}

private class DevExtensionResolver(
    finder: ArchiveFinder<*>, privilegeManager: PrivilegeManager, parent: ClassLoader, environment: ExtLoaderEnvironment
) : ExtensionResolver(
     finder, privilegeManager, parent, environment
)