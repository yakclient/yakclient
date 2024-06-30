package dev.extframework.components.extloader.api.target

import dev.extframework.components.extloader.api.environment.EnvironmentAttribute
import dev.extframework.components.extloader.api.environment.EnvironmentAttributeKey
import dev.extframework.minecraft.bootstrapper.ExtraClassProvider

public interface ExtraClassProviderAttribute : EnvironmentAttribute, ExtraClassProvider {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtraClassProviderAttribute

    public companion object : EnvironmentAttributeKey<ExtraClassProviderAttribute>
}