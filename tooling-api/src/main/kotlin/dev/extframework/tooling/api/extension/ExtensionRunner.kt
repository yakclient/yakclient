package dev.extframework.tooling.api.extension

import com.durganmcbroom.jobs.Job
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey

public interface ExtensionRunner : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionRunner

    public fun init(node: ExtensionNode) : Job<Unit>

    public companion object : EnvironmentAttributeKey<ExtensionRunner>
}