package net.yakclient.components.extloader

import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositorySettings

public data class ExtLoaderConfiguration(
    val mcVersion: String,
    val mcArgs: List<String>,
    val environment: ExtLoaderEnvironmentConfiguration,
) : ComponentConfiguration

public data class ExtLoaderExtConfiguration(
    val descriptor: ExtensionDescriptor,
    val repository: ExtensionRepositorySettings,
)

public enum class ExtLoaderEnvironmentType {
    PROD,
    EXT_DEV,
    INTERNAL_DEV
}

public data class ExtLoaderEnvironmentConfiguration(
    val type: ExtLoaderEnvironmentType,
    val configuration: ContextNodeValue
)