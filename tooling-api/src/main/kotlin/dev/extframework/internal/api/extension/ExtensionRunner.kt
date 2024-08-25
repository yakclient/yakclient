package dev.extframework.internal.api.extension

import com.durganmcbroom.jobs.Job
import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey

public interface ExtensionRunner : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionRunner

    public fun init(node: ExtensionNode) : Job<Unit>

    public companion object : EnvironmentAttributeKey<ExtensionRunner>
}