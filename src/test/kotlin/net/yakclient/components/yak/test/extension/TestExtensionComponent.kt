package net.yakclient.components.yak.test.extension

import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.archives.Archives
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.boot.BootContext
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.createMavenProvider
import net.yakclient.boot.dependency.DependencyProviders
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.withBootDependencies
import net.yakclient.components.yak.YakSoftwareComponent
import net.yakclient.components.yak.extension.ExtensionContext
import net.yakclient.components.yak.extension.ExtensionGraph
import net.yakclient.components.yak.extension.artifact.ExtensionArtifactRequest
import net.yakclient.components.yak.extension.artifact.ExtensionRepositorySettings
import net.yakclient.components.yak.mapping.mapClassName
import net.yakclient.components.yak.mapping.withDots
import net.yakclient.components.yak.mapping.withSlashes
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapper
import net.yakclient.minecraft.bootstrapper.MixinMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.Test

class TestExtensionComponent {
    @Test
    fun `Start component graph`() {
        val cache = Files.createTempDirectory("m2").resolve(UUID.randomUUID().toString()).toString()

        val bootContext = BootContext(
            DependencyProviders()
        )
        bootContext.dependencyProviders.add(
            createMavenProvider(cache, withBootDependencies {
                val yakCentral = SimpleMaven.createContext(
                    SimpleMavenRepositorySettings.default(
                        "http://repo.yakclient.net/snapshots",
                        preferredHash = HashType.SHA1
                    )
                )

                yakCentral.it("net.yakclient:archive-mapper:1.0-SNAPSHOT")
            })
        )
        YakSoftwareComponent().onEnable(
            ComponentContext(
                mapOf(
                    "cache" to cache,
                    "extensions" to "net.yakclient.extensions:example-extension:1.0-SNAPSHOT->/Users/durgan/.m2/repository@local"
                ),
                bootContext
            )
        )
    }

    // TODO Figure out why it throws an exception when the default minecraft modules contains a _ and is open.
    @Test
    fun `Load extension`() {
        val cache = Files.createTempDirectory("m2").resolve(UUID.randomUUID().toString()).toString()

        val bootContext = BootContext(
            DependencyProviders()
        )
        bootContext.dependencyProviders.add(
            createMavenProvider(cache, withBootDependencies {
                val yakCentral = SimpleMaven.createContext(
                    SimpleMavenRepositorySettings.default(
                        "http://repo.yakclient.net/snapshots",
                        preferredHash = HashType.SHA1
                    )
                )

                yakCentral.it("net.yakclient:archive-mapper:1.0-SNAPSHOT")
            })
        )

        val context = ComponentContext(
            mapOf(),
            bootContext
        )


        MinecraftBootstrapper().onEnable(
            ComponentContext(
                mapOf(
                    "version" to "1.19.2",
                    "repository" to "/Users/durgan/.m2/repository",
                    "repositoryType" to "LOCAL",
                    "cache" to cache,
                    "providerVersionMappings" to "file:///Users/durgan/IdeaProjects/durganmcbroom/minecraft-bootstrapper/cache/version-mappings.json",
                    "mcArgs" to "--version;1.19.2;--accessToken;"
                ),
                bootContext
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
            Archives.Finders.JPM_FINDER,
            PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
            this::class.java.classLoader,
            context.bootContext.dependencyProviders,
            context,
            yakContext,
            mappings,
            minecraftHandler.minecraftReference.archive,
        )

        val cacheResult = graph.cacherOf(
            ExtensionRepositorySettings.local("/Users/durgan/.m2/repository")
        ).cache(
            ExtensionArtifactRequest("net.yakclient.extensions:example-extension:1.0-SNAPSHOT")
        )

        println(cacheResult)

        val node1 = graph.get(ExtensionArtifactRequest("net.yakclient.extensions:example-extension:1.0-SNAPSHOT"))
        println(node1)

        val flatMap = node1.orNull()?.let { node ->
            if (node.runtimeModel.mixins.isNotEmpty()) checkNotNull(node.archiveReference) { "Extension has registered mixins but no archive! Please remove this mixins or add a archive." }
            node.runtimeModel.mixins.flatMap { mixin ->
                mixin.injections.map {
                    val provider = yakContext.injectionProviders[it.type]
                        ?: throw IllegalArgumentException("Unknown mixin type: '${it.type}' in mixin class: '${mixin.classname}'")

                    MixinMetadata(
                        provider.parseData(it.options, node.archiveReference!!),
                        provider.get() as MixinInjection<MixinInjection.InjectionData>
                    ) to mappings.mapClassName(mixin.destination.withSlashes()).withDots()
                }
            }

        }
        flatMap?.forEach { (it, to) -> minecraftHandler.registerMixin(to, it) }

        // Init minecraft
        minecraftHandler.writeAll()
        minecraftHandler.loadMinecraft()

        val ref = node1.orNull()?.extension?.process?.ref
        ref?.supplyMinecraft(minecraftHandler.archive)
        ref?.extension?.init(ExtensionContext(
            context,
            yakContext
        ))

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