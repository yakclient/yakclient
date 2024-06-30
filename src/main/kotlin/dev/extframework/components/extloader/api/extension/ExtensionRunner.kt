package dev.extframework.components.extloader.api.extension

import dev.extframework.components.extloader.api.environment.EnvironmentAttribute
import dev.extframework.components.extloader.api.environment.EnvironmentAttributeKey
import dev.extframework.components.extloader.extension.ExtensionNode

public interface ExtensionRunner : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionRunner

    public fun init(node: ExtensionNode)

    public companion object : EnvironmentAttributeKey<ExtensionRunner>
}