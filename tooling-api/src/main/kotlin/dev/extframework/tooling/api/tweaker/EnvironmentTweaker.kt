package dev.extframework.tooling.api.tweaker

import com.durganmcbroom.jobs.Job
import dev.extframework.tooling.api.environment.ExtensionEnvironment

public interface EnvironmentTweaker {
    public fun tweak(environment: ExtensionEnvironment) : Job<Unit>
}