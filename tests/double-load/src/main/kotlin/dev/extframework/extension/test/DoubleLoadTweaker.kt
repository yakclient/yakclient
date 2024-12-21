package dev.extframework.extension.test

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker

public class DoubleLoadTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {

    }
}