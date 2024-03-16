package net.yakclient.components.extloader.api.extension

import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.environment.getOrNull
import net.yakclient.components.extloader.extension.ExtensionNode

public interface ExtensionNodeObserver : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionNodeObserver
    public fun observe(node: ExtensionNode)

    public companion object : EnvironmentAttributeKey<ExtensionNodeObserver>
}

public fun ExtLoaderEnvironment.observeNodes(observer: (ExtensionNode) -> Unit) {
    val realObserver = get(ExtensionNodeObserver).getOrNull()?.let {
        object: ExtensionNodeObserver {
            override fun observe(node: ExtensionNode) {
                it.observe(node)
                observer(node)
            }
        }
    } ?: object  : ExtensionNodeObserver {
        override fun observe(node: ExtensionNode) {
            observer(node)
        }
    }

    this += realObserver
}