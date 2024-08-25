package dev.extframework.internal.api.tweaker

import com.durganmcbroom.jobs.Job
import dev.extframework.internal.api.environment.ExtensionEnvironment

public interface EnvironmentTweaker {
    public fun tweak(environment: ExtensionEnvironment) : Job<Unit>
}