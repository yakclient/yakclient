package net.yakclient.components.extloader

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.target.AppArchiveReference
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.api.target.ExtraClassProviderAttribute
import net.yakclient.components.extloader.workflow.ExtDevWorkflow
import net.yakclient.components.extloader.workflow.ExtLoaderWorkflow
import net.yakclient.components.extloader.workflow.ExtLoaderWorkflowContext
import net.yakclient.minecraft.bootstrapper.*
import java.nio.file.Path

public const val EXT_LOADER_GROUP: String = "net.yakclient.components"
public const val EXT_LOADER_ARTIFACT: String = "ext-loader"
public const val EXT_LOADER_VERSION: String = "1.1-SNAPSHOT"

public class ExtensionLoader(
    private val boot: BootInstance,
    private val configuration: ExtLoaderConfiguration,
    private val minecraft: MinecraftBootstrapper,
) : ComponentInstance<ExtLoaderConfiguration> {
    private fun createMinecraftApp(
        environment: ExtLoaderEnvironment,
        minecraftHandler: MinecraftHandler<*>,
        shutdown: () -> Unit
    ) = object : ApplicationTarget {
        override val reference: AppArchiveReference = object : AppArchiveReference {
            override val reference: ArchiveReference by minecraftHandler.minecraftReference::archive
            override val dependencyReferences: List<ArchiveReference> by minecraftHandler.minecraftReference::libraries
            override val descriptor: SimpleMavenDescriptor =
                SimpleMavenDescriptor("net.minecraft", "minecraft", minecraftHandler.version, null)
            override val handle: ArchiveHandle by lazy { minecraftHandler.handle.archive }
            override val dependencyHandles: List<ArchiveHandle> by lazy { minecraftHandler.handle.libraries }
            override val handleLoaded: Boolean by minecraftHandler::isLoaded

            override fun load(parent: ClassLoader) : Job<Unit> =
                minecraftHandler.loadMinecraft(
                    parent,
                    environment[ExtraClassProviderAttribute].getOrNull() ?: object : ExtraClassProvider {
                        override fun getByteArray(name: String): ByteArray? = null
                    })
        }

        override fun mixin(destination: String, transformer: MinecraftClassTransformer) {
            minecraftHandler.registerMixin(destination, transformer)
        }
        override fun mixin(destination: String, priority: Int, transformer: MinecraftClassTransformer) {
            minecraftHandler.registerMixin(destination, priority, transformer)
        }

//        override fun newMixinTransaction(): MixinTransaction = object : MixinTransaction {
//            override var finished: Boolean = false
//
//            override fun register(destination: String, metadata: MixinTransaction.Metadata<*>) {
//                check(!finished) { "This transaction has ended! Create new one to inject more mixins." }
//
//                minecraftHandler.registerMixin(
//                    destination, MixinMetadata(
//                        metadata.data,
//                        metadata.injection as MixinInjection<MixinInjection.InjectionData>
//                    )
//                )
//            }
//
//            override fun writeAll() {
//                minecraftHandler.writeAll()
//            }
//        }

        override fun start(args: Array<String>) {
            minecraftHandler.startMinecraft(args)
        }

        override fun end() = shutdown()
    }

    override fun start(): Job<Unit> = job {
        minecraft.start()().merge()

        val workflow = when (configuration.environment.type) {
            ExtLoaderEnvironmentType.PROD -> TODO()
            ExtLoaderEnvironmentType.EXT_DEV -> ExtDevWorkflow()
            ExtLoaderEnvironmentType.INTERNAL_DEV -> ExtDevWorkflow() //todo
        }

        fun <T : ExtLoaderWorkflowContext> ExtLoaderWorkflow<T>.parseAndRun(
            node: ContextNodeValue,
            environment: ExtLoaderEnvironment
        ): Job<Unit> {
            val runtimeInfo = minecraft.minecraftHandler.minecraftReference.runtimeInfo

            return work(
                parse(node), environment, configuration.mcArgs.toTypedArray() + arrayOf(
                    "--assetsDir",
                    runtimeInfo.assetsPath.toString(),
                    "--assetIndex",
                    runtimeInfo.assetsName,
                    "--gameDir",
                    runtimeInfo.gameDir.toString(),
                    "--version", minecraft.minecraftHandler.version
                )
            )
        }

        val env = ExtLoaderEnvironment()

        env += ArchiveGraphAttribute(boot.archiveGraph)
        env += DependencyTypeContainerAttribute(
            boot.dependencyTypes
        )
        env += createMinecraftApp(env, minecraft.minecraftHandler, minecraft::end)
        env += WorkingDirectoryAttribute(boot.location)
        env += ParentClassloaderAttribute(ExtensionLoader::class.java.classLoader)
        workflow.parseAndRun(configuration.environment.configuration, env)().merge()
//        registerBasicProviders()
//        InternalRegistry.extensionMappingContainer.register(
//            MOJANG_MAPPING_TYPE,
//            MojangExtensionMappingProvider(boot.location resolve MOJANG_MAPPING_CACHE)
//        )
//        InternalRegistry.extensionMappingContainer.register(
//            EMPTY_MAPPING_TYPE,
//            EmptyExtensionMappingProvider()
//        )
//        InternalRegistry.dependencyTypeContainer = boot.dependencyTypes
//        mapOf(
//            AFTER_BEGIN to InjectionPoints.AfterBegin(),
//            BEFORE_END to InjectionPoints.BeforeEnd(),
//            BEFORE_INVOKE to InjectionPoints.BeforeInvoke(),
//            BEFORE_RETURN to InjectionPoints.BeforeReturn(),
//            OVERWRITE to InjectionPoints.Overwrite(),
//        ).forEach { InternalRegistry.injectionPointContainer.register(it.key, it.value) }

//        graph = ExtensionGraph(
//            boot.location resolve EXTENSION_CACHE,
//            Archives.Finders.ZIP_FINDER,
//            PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
//            this::class.java.classLoader,
//            boot.dependencyTypes,
//            minecraft.minecraftHandler.minecraftReference.archive,
//            minecraft.minecraftHandler.version
//        )
//
//        val localExtensions = runBlocking(bootFactories()) {
//            configuration.extension.map { (request, settings) ->
//                async {
//                    graph.get(request).fix {
//                        graph.cacherOf(settings).cache(ExtensionArtifactRequest(request)).orThrow()
//
//                        graph.get(request).orThrow()
//                    }
//                }
//            }.awaitAll()
//        }

//        this.extensions.addAll(localExtensions)

//        val minecraftHandler = minecraft.minecraftHandler
//
//        this.extensions.forEach {
//            it.extension?.process?.ref?.injectMixins(minecraftHandler::registerMixin)
//        }


        // Setup class providers and let them be initialized later

    }

    // Shutdown of minecraft will trigger extension shutdown
    override fun end() : Job<Unit> = job {
//        extensions.forEach {
//            it.extension?.process?.ref?.extension?.cleanup()
//        }

        minecraft.end()
    }

    internal companion object {
        private const val EXTENSION_CACHE = "extensions"
        private val MOJANG_MAPPING_CACHE = Path.of("mappings") resolve "mojang"
    }
}