package dev.extframework.components.extloader

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.BootInstance
import dev.extframework.boot.component.ComponentInstance
import dev.extframework.boot.component.context.ContextNodeValue
import dev.extframework.components.extloader.api.environment.*
import dev.extframework.components.extloader.api.exception.ExceptionContextSerializer
import dev.extframework.components.extloader.api.exception.StackTracePrinter
import dev.extframework.components.extloader.api.exception.StructuredException
import dev.extframework.components.extloader.api.target.AppArchiveReference
import dev.extframework.components.extloader.api.target.ApplicationTarget
import dev.extframework.components.extloader.api.target.ExtraClassProviderAttribute
import dev.extframework.components.extloader.environment.registerBasicSerializers
import dev.extframework.components.extloader.exception.BasicExceptionPrinter
import dev.extframework.components.extloader.exception.ExtLoaderExceptions
import dev.extframework.components.extloader.exception.handleException
import dev.extframework.components.extloader.workflow.ExtDevWorkflow
import dev.extframework.components.extloader.workflow.ExtLoaderWorkflow
import dev.extframework.components.extloader.workflow.ExtLoaderWorkflowContext
import dev.extframework.minecraft.bootstrapper.ExtraClassProvider
import dev.extframework.minecraft.bootstrapper.MinecraftBootstrapper
import dev.extframework.minecraft.bootstrapper.MinecraftClassTransformer
import dev.extframework.minecraft.bootstrapper.MinecraftHandler
import kotlin.system.exitProcess

public const val EXT_LOADER_GROUP: String = "dev.extframework.components"
public const val EXT_LOADER_ARTIFACT: String = "ext-loader"
public const val EXT_LOADER_VERSION: String = "1.1.1-SNAPSHOT"

// TODO this isnt correct
internal var preloadedMavenArchives: List<SimpleMavenDescriptor> = ArrayList()
    private set

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

            override fun load(parent: ClassLoader): Job<Unit> =
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

        override fun start(args: Array<String>) {
            minecraftHandler.startMinecraft(args)
        }

        override fun end() = shutdown()
    }

    override fun start(): Job<Unit> = job {
        preloadedMavenArchives = boot.archiveGraph
            .map { it.key }
            .filterIsInstance<SimpleMavenDescriptor>()

        val env = ExtLoaderEnvironment()
        env += MutableObjectSetAttribute<ExceptionContextSerializer<*>>(
            exceptionContextSerializers,
        ).registerBasicSerializers()
        env += BasicExceptionPrinter()

        minecraft.start()()
            .mapException {
                StructuredException(
                    ExtLoaderExceptions.MinecraftBootstrapStartException,
                    cause = it
                )
            }
            .handleStructuredException(env)

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

        env += ArchiveGraphAttribute(boot.archiveGraph)
        env += DependencyTypeContainerAttribute(
            boot.dependencyTypes
        )
        env += createMinecraftApp(env, minecraft.minecraftHandler, minecraft::end)
        env += WorkingDirectoryAttribute(boot.location)
        env += ParentClassloaderAttribute(ExtensionLoader::class.java.classLoader)

        workflow.parseAndRun(configuration.environment.configuration, env)().handleStructuredException(env)
    }

    private fun <T> Result<T>.handleStructuredException(
        env: ExtLoaderEnvironment
    ) {
        exceptionOrNull()?.run {
            if (this !is StructuredException) {
                throw this
            } else {
                handleException(env[exceptionContextSerializers].extract(), env[StackTracePrinter].extract(), this)
                exitProcess(-1)
            }
        }
    }

    // Shutdown of minecraft will trigger extension shutdown
    override fun end(): Job<Unit> = job {
        minecraft.end()
    }
}