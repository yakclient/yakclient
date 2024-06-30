package dev.extframework.components.extloader.api.tweaker

import com.durganmcbroom.jobs.Job
import dev.extframework.components.extloader.api.environment.ExtLoaderEnvironment

public interface EnvironmentTweaker {
    public fun tweak(environment: ExtLoaderEnvironment) : Job<Unit>
}