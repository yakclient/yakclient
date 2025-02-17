package dev.extframework.tooling.api.extension

import com.durganmcbroom.jobs.Job
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey

public interface ExtensionInitializer : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionInitializer

    public fun init(node: ExtensionNode) : Job<Unit>

    public companion object : EnvironmentAttributeKey<ExtensionInitializer>
}