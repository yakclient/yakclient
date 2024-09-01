package dev.extframework.test.extension

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.tweaker.EnvironmentTweaker

class TestInitTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        hasTweaked = true
    }

    companion object {
        var hasTweaked: Boolean = false
    }
}