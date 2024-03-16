package net.yakclient.components.extloader.workflow

import com.durganmcbroom.jobs.Job
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment

public interface ExtLoaderWorkflowContext

public interface ExtLoaderWorkflow<T: ExtLoaderWorkflowContext> {
    public val name: String

    public fun parse(node: ContextNodeValue) : T

    public fun work(context: T, environment: ExtLoaderEnvironment, args: Array<String>) : Job<Unit>
}

