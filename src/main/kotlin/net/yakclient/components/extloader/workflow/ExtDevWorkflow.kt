package net.yakclient.components.extloader.workflow

import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.job
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.transformArchive
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.Archives
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.boot.loader.*
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.common.util.immutableLateInit
import net.yakclient.components.extloader.api.environment.ApplicationMappingType
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.environment.MutableObjectSetAttribute
import net.yakclient.components.extloader.api.environment.WorkingDirectoryAttribute
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
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files

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
        context: ExtDevWorkflowContext, env: ExtLoaderEnvironment
    ): JobResult<Unit, Throwable> = job(JobName("Run extension dev workflow")) {
        // Create initial environment
        var environment =
            env + ExtensionDevEnvironment(env[WorkingDirectoryAttribute]!!.path) + ApplicationMappingType(context.mappingType)

        // Add dev graph to environment
        environment += DevExtensionGraph(
            Archives.Finders.ZIP_FINDER,
            PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
            environment[net.yakclient.components.extloader.api.environment.ParentClassloaderAttribute]!!.cl,
            environment,
        )

        // Get extensions graph
        val graph = environment[ExtensionGraph]!!

        // Load a cacher and attempt to cache the extension request
        graph.cacherOf(context.repository).cache(
            ExtensionArtifactRequest(
                context.extension, includeScopes = setOf("compile", "runtime", "import")
            )
        ).attempt()
        // Attempt to load the extension
        val extensionNode = graph.get(context.extension).attempt()

        /*
TODO Make extension graph not load tweakers, there should be seperate tweaker graph that does that
 so tweakers can make modifications to their own extensions
*/
        suspend fun applyTweakers(environment: ExtLoaderEnvironment): ExtLoaderEnvironment {
            return environment[ExtensionGraph]!!.get(context.extension).attempt().tweaker?.tweak(environment)
                ?: environment
        }

        // Apply all tweakers to the environment
        environment = applyTweakers(environment)

        fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
            return node.children.flatMapTo(HashSet(), ::allExtensions) + node
        }

        // Get all extension nodes in order
        val extensions = allExtensions(extensionNode)

        // Get extension observer (if there is one after tweaker application) and observer each node
        environment[ExtensionNodeObserver]?.let { extensions.forEach(it::observe) }

        val appTarget = environment[ApplicationTarget]!!
        val appReference = appTarget.reference
        val mcVersion by appReference.descriptor::version
        val mapperObjects = environment[MutableObjectSetAttribute.Key<MappingsProvider>("mapping-providers")]!!
        val mappingGraph = newMappingsGraph(mapperObjects)

        transformArchive(
            appReference.reference, appReference.dependencyReferences, mappingGraph.findShortest(
                MojangExtensionMappingProvider.FAKE_TYPE, extensionNode.erm.mappingType
            ).forIdentifier(mcVersion), MappingDirection.TO_REAL
        )

        val mixinTransaction = appTarget.newMixinTransaction()
        extensions.forEach {
            it.extension?.process?.ref?.injectMixins(mixinTransaction::register)
        }

        var mcClassProvider: ClassProvider by immutableLateInit()
        var extClassProvider: ClassProvider by immutableLateInit()

        // Create linker with delegating to the uninitialized class providers
        val linker = MinecraftLinker(
            extensions = object : ClassProvider {
                override val packages: Set<String>
                    get() = extClassProvider.packages

                override fun findClass(name: String): Class<*>? = extClassProvider.findClass(name)

                override fun findClass(name: String, module: String): Class<*>? =
                    extClassProvider.findClass(name, module)
            },
            minecraft = object : ClassProvider {
                override val packages: Set<String>
                    get() = mcClassProvider.packages

                override fun findClass(name: String): Class<*>? = mcClassProvider.findClass(name)

                override fun findClass(name: String, module: String): Class<*>? =
                    mcClassProvider.findClass(name, module)
            },
            extensionsSource = object : SourceProvider {
                override val packages: Set<String> = setOf()

                override fun getResource(name: String): URL? =

                    extensions.firstNotNullOfOrNull { it.archive?.classloader?.getResource(name) }

                override fun getResource(name: String, module: String): URL? = getResource(name)

                override fun getSource(name: String): ByteBuffer? = null
            },
            minecraftSource = object : SourceProvider {
                override val packages: Set<String> = setOf()

                override fun getResource(name: String): URL? = appReference.handle.classloader.getResource(name)

                override fun getResource(name: String, module: String): URL? = getResource(name)

                override fun getSource(name: String): ByteBuffer? = null
            },
        )

        // Create the minecraft parent classloader with the delegating linker
        val parentLoader = IntegratedLoader(
            sp = linker.extensionSourceProvider,
            cp = linker.extensionClassProvider,
            parent = this::class.java.classLoader
        )

        // Init minecraft
        mixinTransaction.writeAll()
        appTarget.reference.load(parentLoader)

        // Initialize the first clas provider to allow extensions access to minecraft
        mcClassProvider =
            DelegatingClassProvider((appReference.dependencyHandles + appReference.handle).map(::ArchiveClassProvider))

        // Setup extensions, dont init yet
        extensions.forEach {
            val ref = it.extension?.process?.ref
            ref?.setup(linker)
        }

        // Setup the extension class provider for minecraft
        // TODO this will not support loading new extensions at runtime.
        extClassProvider =
            DelegatingClassProvider(extensions.mapNotNull(ExtensionNode::archive).map(::ArchiveClassProvider))

        // Call init on all extensions, this is ordered correctly
        extensions.forEach(environment[ExtensionRunner]!!::init)

        // Start minecraft
        appTarget.start()
    }
}

private class DevExtensionGraph(
    finder: ArchiveFinder<*>, privilegeManager: PrivilegeManager, parent: ClassLoader, environment: ExtLoaderEnvironment
) : ExtensionGraph(
    Files.createTempDirectory("yakclient-ext"), finder, privilegeManager, parent, environment
)