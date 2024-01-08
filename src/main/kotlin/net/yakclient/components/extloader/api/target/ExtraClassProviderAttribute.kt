package net.yakclient.components.extloader.api.target

import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.minecraft.bootstrapper.ExtraClassProvider

public interface ExtraClassProviderAttribute : EnvironmentAttribute, ExtraClassProvider {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtraClassProviderAttribute

    public companion object : EnvironmentAttributeKey<ExtraClassProviderAttribute>
}