package dev.extframework.extension.test.app

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.environment.partitionLoadersAttrKey
import dev.extframework.internal.api.tweaker.EnvironmentTweaker

public class BlackboxAppTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        environment[partitionLoadersAttrKey].extract().container.register(
            "target", TestAppPartitionLoader(environment)
        )
    }
}