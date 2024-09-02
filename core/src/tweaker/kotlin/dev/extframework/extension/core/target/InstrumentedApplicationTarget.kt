package dev.extframework.extension.core.target

import dev.extframework.extension.core.mixin.MixinAgent
import dev.extframework.internal.api.target.ApplicationTarget

public interface InstrumentedApplicationTarget : ApplicationTarget {
    public val delegate: ApplicationTarget
    public val agents: List<MixinAgent>

    public fun registerAgent(agent: MixinAgent)

    public fun redefine(name: String)
}