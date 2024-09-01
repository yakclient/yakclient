package dev.extframework.extloader.workflow

import com.durganmcbroom.jobs.Job
import dev.extframework.internal.api.environment.ExtensionEnvironment

public sealed interface WorkflowContext

public sealed interface Workflow<T: WorkflowContext> {
    public val name: String

    public fun work(context: T, environment: ExtensionEnvironment) : Job<Unit>
}

