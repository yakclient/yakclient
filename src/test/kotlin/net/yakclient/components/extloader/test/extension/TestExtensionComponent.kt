package net.yakclient.components.extloader.test.extension

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.test.testBootInstance
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.ExtLoaderConfiguration
import net.yakclient.components.extloader.ExtLoaderEnvironmentConfiguration
import net.yakclient.components.extloader.ExtLoaderEnvironmentType
import net.yakclient.components.extloader.ExtensionLoaderFactory
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperFactory
import java.nio.file.Path
import kotlin.test.Test


class TestExtensionComponent {
    @Test
    fun `Load extension`() {
        val cache =
            Path.of(System.getProperty("user.dir")) resolve "src" resolve "test" resolve "resources" resolve "run-cache"
        println("THING IS HERE: $cache")

        val dependencies = setOf(
            "net.yakclient:archive-mapper:1.2-SNAPSHOT",
            "net.yakclient:archive-mapper-transform:1.2-SNAPSHOT",
            "net.yakclient:archive-mapper-proguard:1.2-SNAPSHOT",
            "net.yakclient:launchermeta-handler:1.0-SNAPSHOT",
            "io.arrow-kt:arrow-core:1.1.2",
            "net.yakclient:object-container:1.0-SNAPSHOT",
            "net.yakclient:archives-mixin:1.1-SNAPSHOT",
            "net.yakclient:boot:1.1-SNAPSHOT",
            "com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT",
            "com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT",
            "net.yakclient:common-util:1.0-SNAPSHOT",
            "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3",
            "com.durganmcbroom:jobs:1.0-SNAPSHOT",
            "com.durganmcbroom:jobs-logging:1.0-SNAPSHOT",
            "com.durganmcbroom:jobs-progress:1.0-SNAPSHOT",
            "com.durganmcbroom:jobs-progress-simple:1.0-SNAPSHOT"
        ).mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }

        val boot = testBootInstance(
            mapOf(
                SoftwareComponentDescriptor(
                    "net.yakclient.components",
                    "minecraft-bootstrapper",
                    "1.0-SNAPSHOT", null
                ) to MinecraftBootstrapperFactory::class.java
            ), cache,
            dependencies = dependencies
        )

        val value = mapOf(
            "extension" to mapOf(
                "descriptor" to mapOf(
                    "groupId" to "net.yakclient.extensions",
                    "artifactId" to "example-extension",
                    "version" to "1.0-SNAPSHOT"
                ),
                "repository" to mapOf(
                    "type" to "local",
                    "location" to mavenLocal
                )
            ),
            "mappingType" to "mojang:deobfuscated"
        )

        val instance = ExtensionLoaderFactory(boot).new(
            ExtLoaderConfiguration(
                "1.20.1",
                listOf("--accessToken", ""),
                ExtLoaderEnvironmentConfiguration(
                    ExtLoaderEnvironmentType.INTERNAL_DEV,
                    ContextNodeTypes.newValueType(value)
                )
            )
        )

        instance.start()
//
//        val minecraftHandler = MinecraftBootstrapper.instance.minecraftHandler
//        val mappings = minecraftHandler.minecraftReference.mappings
//        val yakContext = YakSoftwareComponent.createContext(
//            context,
//            mappings
//        )
//
//        val graph = ExtensionGraph(
//            Path.of(cache),
//            Archives.Finders.ZIP_FINDER,
//            PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
//            this::class.java.classLoader,
//            context.boot.dependencyProviders,
//            context,
//            yakContext,
//            mappings,
//            minecraftHandler.minecraftReference.archive,
//            minecraftHandler.version,
//        )
//
//        val cacheResult = graph.cacherOf(
//            ExtensionRepositorySettings.local()
//        ).cache(
//            ExtensionArtifactRequest("net.yakclient.extensions:example-extension:1.0-SNAPSHOT")
//        )
//
//        println(cacheResult)
//
//        val node1 = graph.get(ExtensionArtifactRequest("net.yakclient.extensions:example-extension:1.0-SNAPSHOT")).orNull()
//        println(node1)
//
//        val flatMap = node1?.let { node ->
//           val allMixins = node.archiveReference?.enabledPartitions
//                ?.flatMap(ExtensionVersionPartition::mixins) ?: ArrayList()
//
//            if (allMixins.isNotEmpty()) checkNotNull(node.archiveReference) { "Extension has registered mixins but no archive! Please remove mixins or add an archive." }
//            val flatMap = allMixins.flatMap { mixin: ExtensionMixin ->
//                mixin.injections.map {
//                    val provider = yakContext.injectionProviders[it.type]
//                        ?: throw IllegalArgumentException("Unknown mixin type: '${it.type}' in mixin class: '${mixin.classname}'")
//
//                    MixinMetadata(
//                        provider.parseData(it.options, node.archiveReference!!),
//                        provider.get() as MixinInjection<MixinInjection.InjectionData>
//                    ) to (mappings.mapClassName(mixin.destination.withSlashes(), MappingDirection.TO_FAKE)?.withDots() ?: mixin.destination.withDots())
//                }
//            }
//            flatMap
//        }
//        flatMap?.forEach { (it, to) -> minecraftHandler.registerMixin(to, it) }
//
//        // Init minecraft
//        minecraftHandler.writeAll()
//        minecraftHandler.loadMinecraft()
//
//        val ref = node1?.extension?.process?.ref
//        ref?.supplyMinecraft(minecraftHandler.archive)
//        ref?.extension?.init(ExtensionContext())
//
//        node1?.also {
//
//            val ref = it.extension?.process?.ref
//            val extension = ref?.extension
//
//            extension?.init(ExtensionContext())
//        }
//
//        minecraftHandler.startMinecraft()
    }
}