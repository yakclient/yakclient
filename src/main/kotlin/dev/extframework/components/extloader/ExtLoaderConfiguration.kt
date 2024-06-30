package dev.extframework.components.extloader

import dev.extframework.boot.component.ComponentConfiguration
import dev.extframework.boot.component.context.ContextNodeValue
import dev.extframework.components.extloader.extension.artifact.ExtensionDescriptor
import dev.extframework.components.extloader.extension.artifact.ExtensionRepositorySettings

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