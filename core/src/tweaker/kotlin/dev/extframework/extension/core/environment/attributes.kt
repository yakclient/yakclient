package dev.extframework.extension.core.environment

import dev.extframework.archives.mixin.SourceInjectionPoint
import dev.extframework.extension.core.delegate.DelegationProvider
import dev.extframework.extension.core.mixin.MixinAgent
import dev.extframework.extension.core.mixin.MixinInjectionProvider
import dev.extframework.extension.core.mixin.MixinSubsystem
import dev.extframework.internal.api.environment.MutableObjectContainerAttribute
import dev.extframework.internal.api.environment.MutableObjectSetAttribute

public val mixinTypesAttrKey: MutableObjectContainerAttribute.Key<MixinInjectionProvider<*, *>> =
    MutableObjectContainerAttribute.Key("mixin-types")
public val injectionPointsAttrKey: MutableObjectContainerAttribute.Key<SourceInjectionPoint> =
    MutableObjectContainerAttribute.Key("injection-points")
public val delegationProvidersAttrKey: MutableObjectSetAttribute.Key<DelegationProvider<*>> =
    MutableObjectSetAttribute.Key("delegation-providers")
public val mixinAgentsAttrKey: MutableObjectSetAttribute.Key<MixinAgent> = MutableObjectSetAttribute.Key("mixin-agents")