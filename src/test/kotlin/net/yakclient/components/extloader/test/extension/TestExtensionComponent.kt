package net.yakclient.components.extloader.test.extension

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.boot.test.testBootInstance
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.*
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositorySettings
import net.yakclient.components.extloader.workflow.ExtDevWorkflowContext
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.Test

fun main() {
    val cache =
        Path.of(System.getProperty("user.dir")) resolve "src" resolve "test" resolve "resources" resolve "run-cache"
    println("THING IS HERE: $cache")

    val boot = testBootInstance(
        mapOf(
            SoftwareComponentDescriptor(
                "net.yakclient.components",
                "minecraft-bootstrapper",
                "1.0-SNAPSHOT", null
            ) to MinecraftBootstrapperFactory::class.java
        ), cache
    )

    val value = mapOf(
        "extension" to mapOf(
            "descriptor" to mapOf(
                "groupId" to "net.yakclient.extensions",
                "artifactId" to "yakgradle-ext-test",
                "version" to "1.0-SNAPSHOT"
            ),
            "repository" to mapOf(
                "type" to "local",
                "location" to mavenLocal
            )
        ),
        "mappingType" to "mojang/deobfuscated"
    )

    val instance = ExtensionLoaderFactory(boot).new(
        ExtLoaderConfiguration(
            "1.20.1", listOf("--accessToken", ""),
            ExtLoaderEnvironmentConfiguration(
                ExtLoaderEnvironmentType.EXT_DEV,
                ContextNodeTypes.newValueType(value)
            )
        )
    )

    instance.start()
}

class TestExtensionComponent {
    @Test
    fun `Load extension`() {
        val cache =
            Path.of(System.getProperty("user.dir")) resolve "src" resolve "test" resolve "resources" resolve "run-cache"
        println("THING IS HERE: $cache")

        val boot = testBootInstance(
            mapOf(
                SoftwareComponentDescriptor(
                    "net.yakclient.components",
                    "minecraft-bootstrapper",
                    "1.0-SNAPSHOT", null
                ) to MinecraftBootstrapperFactory::class.java
            ), cache
        )

        val value = mapOf(
            "extension" to mapOf(
                "descriptor" to mapOf(
                    "groupId" to "net.yakclient.extensions",
                    "artifactId" to "yakgradle-ext-test",
                    "version" to "1.0-SNAPSHOT"
                ),
                "repository" to mapOf(
                    "type" to "local",
                    "location" to mavenLocal
                )
            ),
            "mappingType" to "mojang/deobfuscated"
        )

        val instance = ExtensionLoaderFactory(boot).new(
            ExtLoaderConfiguration(
                "1.20.1",
                listOf("--accessToken", ""),
                ExtLoaderEnvironmentConfiguration(
                    ExtLoaderEnvironmentType.EXT_DEV,
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