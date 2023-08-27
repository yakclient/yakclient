package net.yakclient.components.extloader

import arrow.core.continuations.either
import arrow.core.handleErrorWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.Archives
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.boot.loader.*
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.client.api.*
import net.yakclient.common.util.immutableLateInit
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.extension.ExtensionGraph
import net.yakclient.components.extloader.extension.ExtensionNode
import net.yakclient.components.extloader.extension.MinecraftLinker
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactRequest
import net.yakclient.components.extloader.extension.mapping.EmptyExtensionMappingProvider
import net.yakclient.components.extloader.extension.mapping.EmptyExtensionMappingProvider.Companion.EMPTY_MAPPING_TYPE
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider.Companion.MOJANG_MAPPING_TYPE
import net.yakclient.components.extloader.mixin.InjectionPoints
import net.yakclient.components.extloader.mixin.registerBasicProviders
import net.yakclient.internal.api.InternalRegistry
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapper
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.logging.Logger

public class ExtensionLoader(
        private val boot: BootInstance,
        private val configuration: ExtLoaderConfiguration,
        private val minecraft: MinecraftBootstrapper,
) : ComponentInstance<ExtLoaderConfiguration> {
    private lateinit var graph: ExtensionGraph
    private val extensions = ArrayList<ExtensionNode>()

    override fun start() {
        minecraft.start()

        logger.log
        registerBasicProviders()
        InternalRegistry.extensionMappingContainer.register(
                MOJANG_MAPPING_TYPE,
                MojangExtensionMappingProvider(boot.location resolve MOJANG_MAPPING_CACHE)
        )
        InternalRegistry.extensionMappingContainer.register(
                EMPTY_MAPPING_TYPE,
                EmptyExtensionMappingProvider()
        )
        InternalRegistry.dependencyTypeContainer = boot.dependencyTypes
        mapOf(
                AFTER_BEGIN to InjectionPoints.AfterBegin(),
                BEFORE_END to InjectionPoints.BeforeEnd(),
                BEFORE_INVOKE to InjectionPoints.BeforeInvoke(),
                BEFORE_RETURN to InjectionPoints.BeforeReturn(),
                OVERWRITE to InjectionPoints.Overwrite(),
        ).forEach { InternalRegistry.injectionPointContainer.register(it.key, it.value) }

        graph = ExtensionGraph(
                boot.location resolve EXTENSION_CACHE,
                Archives.Finders.ZIP_FINDER,
                PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
                this::class.java.classLoader,
                boot.dependencyTypes,
                minecraft.minecraftHandler.minecraftReference.archive,
                minecraft.minecraftHandler.version
        )

        val either = either.eager {
            configuration.extension.map { (request, settings) ->
                graph.get(request).handleErrorWith {
                    graph.cacherOf(settings).cache(ExtensionArtifactRequest(request))

                    graph.get(request)
                }
            }.map { it.bind() }
        }

        val nodes = checkNotNull(either.orNull()) { "Failed to load extensions due to exception '$either'" }

        this.extensions.addAll(nodes)

        val minecraftHandler = minecraft.minecraftHandler

        this.extensions.forEach {
            it.extension?.process?.ref?.injectMixins(minecraftHandler::registerMixin)
        }

        // This part has some black magic that i while try to explain here. The main
        // issue is that minecraft needs to be able to load classes from extensions,
        // and extensions need to be able to load classes from minecraft. Other modding
        // systems achieve this by loading everything with one classloader, with our more
        // modular design we opted to not do this, however the drawback is that we now have
        // to deal with circularly dependent classloaders. The first issue is one of infinite
        // recursion (stackoverflow) if extensions are trying to access a class in minecraft,
        // it doesn't have it, so tries to get it from the extensions, which throw it back to
        // minecraft etc. This is fixed with our MinecraftLinker, which you can go check out.
        // This next bit of code then addresses instantiating. Unfortunately, this is a bit messy
        // but is the best way ive found of doing it.

        // Setup class providers and let them be initialized later
        var mcClassProvider: ClassProvider by immutableLateInit()
        var extClassProvider: ClassProvider by immutableLateInit()

        // Create linker with delegating to the uninitialized class providers
        val linker = MinecraftLinker(
                extensions = object : ClassProvider {
                    override val packages: Set<String>
                        get() = extClassProvider.packages

                    override fun findClass(name: String): Class<*>? = extClassProvider.findClass(name)

                    override fun findClass(name: String, module: String): Class<*>? = extClassProvider.findClass(name, module)
                },
                minecraft = object : ClassProvider {
                    override val packages: Set<String>
                        get() = mcClassProvider.packages

                    override fun findClass(name: String): Class<*>? = mcClassProvider.findClass(name)

                    override fun findClass(name: String, module: String): Class<*>? = mcClassProvider.findClass(name, module)
                },
                extensionsSource = object : SourceProvider {
                    override val packages: Set<String> = setOf()

                    override fun getResource(name: String): URL? = extensions.firstNotNullOfOrNull { it.archive?.classloader?.getResource(name) }

                    override fun getResource(name: String, module: String): URL? = getResource(name)

                    override fun getSource(name: String): ByteBuffer? = null
                },
                minecraftSource = object : SourceProvider {
                    override val packages: Set<String> = setOf()

                    override fun getResource(name: String): URL? = minecraftHandler.archive.classloader.getResource(name)

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
        minecraftHandler.writeAll()
        minecraftHandler.loadMinecraft(parentLoader)

        // Initialize the first clas provider to allow extensions access to minecraft
        mcClassProvider = ArchiveClassProvider(minecraftHandler.archive)

        // Setup extensions, dont init yet
        this.extensions.forEach {
            val ref = it.extension?.process?.ref
            ref?.setup(linker)
        }

        // Setup the extension class provider for minecraft
        // TODO this will not support loading new extensions at runtime.
        extClassProvider =
                DelegatingClassProvider(this.extensions.mapNotNull(ExtensionNode::archive).map(::ArchiveClassProvider))

        // Call init on all extensions
        // TODO this may initialize parent extensions before their dependencies
        this.extensions.forEach {
            val extension = it.extension?.process?.ref?.extension

            extension?.init()
        }

        // Start minecraft
        minecraftHandler.startMinecraft()
    }

    override fun end() {
        extensions.forEach {
            it.extension?.process?.ref?.extension?.cleanup()
        }

        minecraft.end()
    }

    internal companion object {
        internal val logger = Logger.getLogger(this::class.simpleName)
        private const val EXTENSION_CACHE = "extensions"
        private val MOJANG_MAPPING_CACHE = Path.of("mappings") resolve "mojang"
    }
}