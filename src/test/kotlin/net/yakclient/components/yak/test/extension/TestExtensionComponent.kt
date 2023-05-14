package net.yakclient.components.yak.test.extension

import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.mapClassName
import net.yakclient.archives.Archives
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.createMavenProvider
import net.yakclient.boot.dependency.DependencyProviders
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.client.api.ExtensionContext
import net.yakclient.components.yak.YakSoftwareComponent
import net.yakclient.components.yak.extension.ExtensionGraph
import net.yakclient.components.yak.extension.ExtensionMixin
import net.yakclient.components.yak.extension.ExtensionVersionPartition
import net.yakclient.components.yak.extension.artifact.ExtensionArtifactRequest
import net.yakclient.components.yak.extension.artifact.ExtensionRepositorySettings
import net.yakclient.components.yak.mapping.withDots
import net.yakclient.components.yak.mapping.withSlashes
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapper
import net.yakclient.minecraft.bootstrapper.MixinMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test

class TestExtensionComponent {
    @Test
    fun `Start component graph`() {
        val cache = Files.createTempDirectory("m2").resolve(UUID.randomUUID().toString()).toString()

        val boot = BootInstance.new(cache)


        YakSoftwareComponent().onEnable(
            ComponentContext(
                mapOf(
                    "cache" to cache,
                    "extensions" to "net.yakclient.extensions:example-extension:1.0-SNAPSHOT->/Users/durgan/.m2/repository@local"
                ),
                boot
            )
        )
    }

    @Test
    fun `Load extension`() {
        val cache = Files.createTempDirectory("m2").resolve(UUID.randomUUID().toString()).toString()
        println("THING IS HERE: $cache")

        val boot = BootInstance.new(cache)

        val context = ComponentContext(
            mapOf(),
            boot
        )


        MinecraftBootstrapper().onEnable(
            ComponentContext(
                mapOf(
                    "version" to "1.19.2",
                    "repository" to "http://maven.yakclient.net/snapshots",
                    "repositoryType" to "DEFAULT",
                    "cache" to cache,
                    "providerVersionMappings" to "file:///Users/durgan/IdeaProjects/durganmcbroom/minecraft-bootstrapper/cache/version-mappings.json",
                    "mcArgs" to "--version;1.19.2;--accessToken;"
                ),
                boot
            )
        )


        val minecraftHandler = MinecraftBootstrapper.instance.minecraftHandler
        val mappings = minecraftHandler.minecraftReference.mappings
        val yakContext = YakSoftwareComponent.createContext(
            context,
            mappings
        )

        val graph = ExtensionGraph(
            Path.of(cache),
            Archives.Finders.ZIP_FINDER,
            PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
            this::class.java.classLoader,
            context.boot.dependencyProviders,
            context,
            yakContext,
            mappings,
            minecraftHandler.minecraftReference.archive,
            minecraftHandler.version,
        )

        val cacheResult = graph.cacherOf(
            ExtensionRepositorySettings.local("/Users/durgan/.m2/repository")
        ).cache(
            ExtensionArtifactRequest("net.yakclient.extensions:example-extension:1.0-SNAPSHOT")
        )

        println(cacheResult)

        val node1 = graph.get(ExtensionArtifactRequest("net.yakclient.extensions:example-extension:1.0-SNAPSHOT")).orNull()
        println(node1)

        val flatMap = node1?.let { node ->
           val allMixins = node.archiveReference?.enabledPartitions
                ?.flatMap(ExtensionVersionPartition::mixins) ?: ArrayList()

            if (allMixins.isNotEmpty()) checkNotNull(node.archiveReference) { "Extension has registered mixins but no archive! Please remove mixins or add an archive." }
            val flatMap = allMixins.flatMap { mixin: ExtensionMixin ->
                mixin.injections.map {
                    val provider = yakContext.injectionProviders[it.type]
                        ?: throw IllegalArgumentException("Unknown mixin type: '${it.type}' in mixin class: '${mixin.classname}'")

                    MixinMetadata(
                        provider.parseData(it.options, node.archiveReference!!),
                        provider.get() as MixinInjection<MixinInjection.InjectionData>
                    ) to (mappings.mapClassName(mixin.destination.withSlashes(), MappingDirection.TO_FAKE)?.withDots() ?: mixin.destination.withDots())
                }
            }
            flatMap
        }
        flatMap?.forEach { (it, to) -> minecraftHandler.registerMixin(to, it) }

        // Init minecraft
        minecraftHandler.writeAll()
        minecraftHandler.loadMinecraft()

        val ref = node1?.extension?.process?.ref
        ref?.supplyMinecraft(minecraftHandler.archive)
        ref?.extension?.init(ExtensionContext())

        node1?.also {

            val ref = it.extension?.process?.ref
            val extension = ref?.extension

            extension?.init(ExtensionContext())
        }

        minecraftHandler.startMinecraft()


//        testEnable(
//            MinecraftBootstrapper(),
//            mapOf(
//                "version" to "1.19.2",
//                "repository" to "/Users/durgan/.m2/repository",
//                "repositoryType" to "LOCAL",
//                "cache" to cache,
//                "providerVersionMappings" to "file:///Users/durgan/IdeaProjects/durganmcbroom/minecraft-bootstrapper/cache/version-mappings.json",
//                "mcArgs" to "--version;1.19.2;--accessToken;"
//            ),
//        )
//
//        testEnable(
//            YakSoftwareComponent(),
//            mapOf(
//                "cache" to System.getProperty("user.dir"),
//                "extensions" to "net.yakclient.extensions:example-extension:1.0-SNAPSHOT->/Users/durgan/.m2/repository@local"
//            ),
//            mavenCache = cache
//        )
    }
}