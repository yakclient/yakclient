package net.yakclient.components.extloader.workflow

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import net.yakclient.archive.mapper.MappingsProvider
import net.yakclient.archive.mapper.findShortest
import net.yakclient.archive.mapper.newMappingsGraph
import net.yakclient.archive.mapper.transform.transformArchive
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.boot.loader.*
import net.yakclient.common.util.immutableLateInit
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionNodeObserver
import net.yakclient.components.extloader.api.extension.ExtensionRunner
import net.yakclient.components.extloader.api.target.ApplicationParentClProvider
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.environment.ExtensionDevEnvironment
import net.yakclient.components.extloader.extension.ExtensionLoadException
import net.yakclient.components.extloader.extension.ExtensionNode
import net.yakclient.components.extloader.extension.ExtensionResolver
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactRequest
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositorySettings
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import net.yakclient.components.extloader.target.TargetLinker
import net.yakclient.components.extloader.tweaker.EnvironmentTweakerNode
import net.yakclient.components.extloader.tweaker.EnvironmentTweakerResolver
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

    override fun work(
        context: ExtDevWorkflowContext, environment: ExtLoaderEnvironment, args: Array<String>
    ): Job<Unit> = job(JobName("Run extension dev workflow")) {
        // Create initial environment
        environment += ExtensionDevEnvironment(environment[WorkingDirectoryAttribute].extract().path)
        environment += ApplicationMappingTarget(
            context.mappingType
        )
        // Add dev graph to environment
        environment += EnvironmentTweakerResolver(
            environment
        )
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

        val tweakerDesc = context.extension.copy(
            classifier = "tweaker"
        )
        val allTweakers = job(JobName("Load and apply tweakers")) {
            fun EnvironmentTweakerNode.applyTweakers(env: ExtLoaderEnvironment) {
                parents.forEach { it.applyTweakers(env) }
                tweaker?.tweak(env)?.invoke()?.merge()
            }

            val tweakerResolver = environment[EnvironmentTweakerResolver].extract()
            val tweakerCacheResult = environment.archiveGraph.cache(
                ExtensionArtifactRequest(tweakerDesc, includeScopes = setOf("compile", "runtime", "import")),
                context.repository,
                tweakerResolver
            )()
            val tweaker = if (tweakerCacheResult.exceptionOrNull() is ArchiveException.ArtifactResolutionException) null
            else if (tweakerCacheResult.isSuccess) environment.archiveGraph.get(
                tweakerDesc,
                tweakerResolver
            )().merge() else throw tweakerCacheResult.exceptionOrNull()!!

            tweaker?.applyTweakers(environment)

            fun allTweakers(node: EnvironmentTweakerNode): List<EnvironmentTweakerNode> {
                return listOf(node) + node.parents.flatMap { allTweakers(it) }
            }

            tweaker?.let(::allTweakers) ?: listOf()
        }().mapException {
            ExtensionLoadException(tweakerDesc, it)
        }.merge()

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
            ExtensionLoadException(context.extension, it)
        }.merge()

        // Get all extension nodes in order
        val extensions = allExtensions(extensionNode)

        // Get extension observer (if there is one after tweaker application) and observer each node
        environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }

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