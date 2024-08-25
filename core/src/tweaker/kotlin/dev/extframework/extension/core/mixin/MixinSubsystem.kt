package dev.extframework.extension.core.mixin

import com.durganmcbroom.jobs.Job

public interface MixinSubsystem : MixinAgent {
    public fun process(
        ctx: MixinProcessContext
    ) : Job<Boolean>
}