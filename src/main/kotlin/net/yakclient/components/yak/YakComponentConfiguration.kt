package net.yakclient.components.yak

import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.components.yak.extension.artifact.ExtensionDescriptor
import net.yakclient.components.yak.extension.artifact.ExtensionRepositorySettings

public data class YakConfiguration(
        val mcVersion: String,
        val mcArgs: List<String>,
        val extension: List<YakExtensionConfiguration>
) : ComponentConfiguration {
}

public data class YakExtensionConfiguration(
        val descriptor: ExtensionDescriptor,
        val repository: ExtensionRepositorySettings,
)