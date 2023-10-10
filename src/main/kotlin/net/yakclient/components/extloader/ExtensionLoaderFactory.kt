package net.yakclient.components.extloader

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.boot.new
import net.yakclient.components.extloader.workflow.check
import net.yakclient.components.extloader.workflow.getCoerceCheckString
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapperConfiguration
import java.lang.IllegalArgumentException

public class ExtensionLoaderFactory(boot: BootInstance) :
    ComponentFactory<ExtLoaderConfiguration, ExtensionLoader>(boot) {
    override fun parseConfiguration(value: ContextNodeValue): ExtLoaderConfiguration {
        val tree = value.coerceTree()
        val environment = tree["environment"].check { "environment" }.coerceTree()

        return ExtLoaderConfiguration(
            tree.getCoerceCheckString("minecraft-version"),
            tree["minecraft-args"]?.coerceArray().check { "minecraft-args" }.list()
                .map { it.coerceType(ContextNodeTypes.String) },
            ExtLoaderEnvironmentConfiguration(
                when (environment.getCoerceCheckString("type")) {
                    "extension-dev" -> ExtLoaderEnvironmentType.EXT_DEV
                    else -> throw IllegalArgumentException(
                        "Unknown environment type: '${
                            environment.getCoerceCheckString("type")
                        }'"
                    )
                },
                environment["context"].check { "environment.context" }
            )
        )
    }

    override fun new(configuration: ExtLoaderConfiguration): ExtensionLoader {
        val isInternalDev = true // TODO configuration.environment.type == ExtLoaderEnvironmentType.INTERNAL_DEV

        val repo = if (isInternalDev)
            SimpleMavenRepositorySettings.local(preferredHash = HashType.SHA1) else
            SimpleMavenRepositorySettings.default(
                "http://maven.yakclient.net/snapshots",
                preferredHash = HashType.SHA1
            )

        val mcConfig = MinecraftBootstrapperConfiguration(
            configuration.mcVersion,
            repo,
            boot.location.resolve("mc").toString(),
            "http://maven.yakclient.net/public/mc-version-mappings.json",
            configuration.mcArgs,
            true
        )

        return ExtensionLoader(
            boot,
            configuration,
            boot.new(
                SoftwareComponentDescriptor(
                    "net.yakclient.components",
                    "minecraft-bootstrapper",
                    "1.0-SNAPSHOT", null
                ), mcConfig
            )
        )
    }
}