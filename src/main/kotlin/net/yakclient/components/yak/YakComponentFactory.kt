package net.yakclient.components.yak

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.context.ContextNodeTree
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.boot.new
import net.yakclient.components.yak.extension.artifact.ExtensionDescriptor
import net.yakclient.components.yak.extension.artifact.ExtensionRepositorySettings
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperConfiguration
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperFactory

public class YakComponentFactory(boot: BootInstance) : ComponentFactory<YakConfiguration, YakSoftwareComponent>(boot) {
    override fun parseConfiguration(value: ContextNodeValue): YakConfiguration {
        val tree = value.coerceTree()

        fun <T : Any> T?.check(name: () -> String): T {
            return checkNotNull(this) { "Error while trying to parse configuration for component: 'Yak'. Could not find property $name'." }
        }

        fun ContextNodeTree.getCoerceCheck(key: String): String {
            return get(key)?.coerceType(ContextNodeTypes.String).check { key }
        }

        fun parseExt(contextNodeValue: ContextNodeValue): YakExtensionConfiguration {
            val extTree = contextNodeValue.coerceTree()

            fun parseDescriptor(tree: ContextNodeTree): ExtensionDescriptor = ExtensionDescriptor(
                    tree.getCoerceCheck("groupId"),
                    tree.getCoerceCheck("artifactId"),
                    tree.getCoerceCheck("version"),
                    null
            )

            fun parseSettings(tree: ContextNodeTree): ExtensionRepositorySettings {
                val repoType = tree.getCoerceCheck("type")
                val repo = tree.getCoerceCheck("location")

                return when (repoType.lowercase()) {
                    "local" -> ExtensionRepositorySettings.local(repo)
                    "default" -> ExtensionRepositorySettings.local(repo)
                    else -> throw IllegalArgumentException("Unknown repository type: '$repoType' for repository : '$repo' ")
                }
            }

            return YakExtensionConfiguration(
                    parseDescriptor(extTree["descriptor"].check { "descriptor" }.coerceTree()),
                    parseSettings(extTree["repository"].check { "descriptor" }.coerceTree())
            )
        }

        return YakConfiguration(
                tree.getCoerceCheck("mcVersion"),
                tree["mcArgs"]?.coerceArray()?.list()?.map { it.coerceType(ContextNodeTypes.String) }.check { "mcArgs" },
                tree["extensions"]?.coerceArray()?.list()?.map(::parseExt).check { "extensions" }
        )
    }

    override fun new(configuration: YakConfiguration): YakSoftwareComponent {
        return YakSoftwareComponent(
                boot,
                configuration,
                boot.new(SoftwareComponentDescriptor(
                        "net.yakclient.components",
                        "minecraft-bootstrapper",
                        "1.0-SNAPSHOT", null
                ), MinecraftBootstrapperConfiguration(
                        configuration.mcVersion,
                        SimpleMavenRepositorySettings.default("http://maven.yakclient.net/snapshots", preferredHash = HashType.SHA1),
                        boot.location.resolve("mc").toString(),
                        "http://maven.yakclient.net/public/mc-version-mappings.json",
                        configuration.mcArgs
                ))
        )

    }
}