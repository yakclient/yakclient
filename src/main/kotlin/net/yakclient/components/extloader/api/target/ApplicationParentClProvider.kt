package net.yakclient.components.extloader.api.target

import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.target.TargetLinker

public interface ApplicationParentClProvider : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ApplicationParentClProvider

    public fun getParent(linker: TargetLinker, environment: ExtLoaderEnvironment) : ClassLoader

    public companion object : EnvironmentAttributeKey<ApplicationParentClProvider> {

    }
}