package net.yakclient.components.extloader.test.extension

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.durganmcbroom.jobs.launch
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.test.testBootInstance
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.ExtLoaderConfiguration
import net.yakclient.components.extloader.ExtLoaderEnvironmentConfiguration
import net.yakclient.components.extloader.ExtLoaderEnvironmentType
import net.yakclient.components.extloader.ExtensionLoaderFactory
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperFactory
import runBootBlocking
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.test.Test

fun main() {
    TestExtensionComponent().`Load extension`()
}

class TestExtensionComponent {
    private fun readDependenciesList(): Set<String> {
        val ins = this::class.java.getResourceAsStream("/dependencies.txt")!!
        return InputStreamReader(ins).use {
            it.readLines().toSet()
        }
    }

    @Test
    fun `Load extension`() {
        val cache =
            Path.of(System.getProperty("user.dir")) resolve "src" resolve "test" resolve "resources" resolve "run-cache"
        println("THING IS HERE: $cache")


        val dependencies = readDependenciesList().mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
            .filterNotTo(HashSet()) { it.artifact == "minecraft-bootstrapper" }

        runBootBlocking {

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

            instance.start()().merge()
        }
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