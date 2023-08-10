package net.yakclient.internal.api

import net.yakclient.archives.mixin.SourceInjectionPoint
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.internal.api.mapping.MappingsProvider
import net.yakclient.internal.api.mixin.MixinInjectionProvider
import net.yakclient.`object`.MutableObjectContainer
import net.yakclient.`object`.ObjectContainerImpl

public object InternalRegistry {
    public val extensionMappingContainer : MutableObjectContainer<MappingsProvider> = ObjectContainerImpl()
    public val mixinTypeContainer: MutableObjectContainer<MixinInjectionProvider<*>> = ObjectContainerImpl()
    public lateinit var dependencyTypeContainer : DependencyTypeContainer
    public val injectionPointContainer : MutableObjectContainer<SourceInjectionPoint> = ObjectContainerImpl()
}