package net.yakclient.components.extloader.api.extension

import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.components.extloader.extension.ExtensionNode

public interface ExtensionRunner : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionRunner

    public fun init(node: ExtensionNode)

    public companion object : EnvironmentAttributeKey<ExtensionRunner>
}