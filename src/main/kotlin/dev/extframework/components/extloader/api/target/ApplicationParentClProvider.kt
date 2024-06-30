package dev.extframework.components.extloader.api.target

import dev.extframework.components.extloader.api.environment.EnvironmentAttribute
import dev.extframework.components.extloader.api.environment.EnvironmentAttributeKey
import dev.extframework.components.extloader.api.environment.ExtLoaderEnvironment
import dev.extframework.components.extloader.target.TargetLinker

public interface ApplicationParentClProvider : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ApplicationParentClProvider

    public fun getParent(linker: TargetLinker, environment: ExtLoaderEnvironment) : ClassLoader

    public companion object : EnvironmentAttributeKey<ApplicationParentClProvider>
}