package net.yakclient.components.yak

import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.components.yak.extension.artifact.ExtensionDescriptor
import net.yakclient.components.yak.extension.artifact.ExtensionRepositorySettings

public data class ExtLoaderConfiguration(
        val mcVersion: String,
        val mcArgs: List<String>,
        val extension: List<ExtLoaderExtConfiguration>
) : ComponentConfiguration {
}

public data class ExtLoaderExtConfiguration(
        val descriptor: ExtensionDescriptor,
        val repository: ExtensionRepositorySettings,
)