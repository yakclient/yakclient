package dev.extframework.components.extloader.workflow

import com.durganmcbroom.jobs.Job
import dev.extframework.boot.component.context.ContextNodeValue
import dev.extframework.components.extloader.api.environment.ExtLoaderEnvironment

public interface ExtLoaderWorkflowContext

public interface ExtLoaderWorkflow<T: ExtLoaderWorkflowContext> {
    public val name: String

    public fun parse(node: ContextNodeValue) : T

    public fun work(context: T, environment: ExtLoaderEnvironment, args: Array<String>) : Job<Unit>
}

