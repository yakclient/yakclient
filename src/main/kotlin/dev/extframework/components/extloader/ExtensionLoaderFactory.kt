package dev.extframework.components.extloader

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.BootInstance
import dev.extframework.boot.component.ComponentFactory
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import dev.extframework.boot.component.context.ContextNodeTypes
import dev.extframework.boot.component.context.ContextNodeValue
import dev.extframework.boot.new
import dev.extframework.components.extloader.workflow.check
import dev.extframework.components.extloader.workflow.getCoerceCheckString
import dev.extframework.minecraft.bootstrapper.MinecraftBootstrapperConfiguration
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
                    "production" -> ExtLoaderEnvironmentType.PROD
                    "internal-dev" -> ExtLoaderEnvironmentType.INTERNAL_DEV
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
        val isInternalDev =  configuration.environment.type == ExtLoaderEnvironmentType.INTERNAL_DEV

        val repo = if (isInternalDev)
            SimpleMavenRepositorySettings.local(preferredHash = ResourceAlgorithm.SHA1) else
            SimpleMavenRepositorySettings.default(
                "https://maven.extframework.dev/snapshots",
                preferredHash = ResourceAlgorithm.SHA1
            )

        val mcConfig = MinecraftBootstrapperConfiguration(
            configuration.mcVersion,
            repo,
            boot.location.resolve("mc").toString(),
            "https://maven.extframework.dev/public/mc-version-mappings.json",
        )

        return ExtensionLoader(
            boot,
            configuration,
            boot.new(
                SoftwareComponentDescriptor(
                    "dev.extframework.components",
                    "minecraft-bootstrapper",
                    "1.0-SNAPSHOT", null
                ), mcConfig
            )
        )
    }
}