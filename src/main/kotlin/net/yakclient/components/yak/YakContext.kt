package net.yakclient.components.yak

import net.yakclient.boot.component.ComponentContext
import net.yakclient.components.yak.mapping.MappingProvider
import net.yakclient.components.yak.mixin.MixinInjectionProvider

public data class YakContext internal constructor(
    public val parent: ComponentContext,
    public val injectionProviders: MutableMap<String, MixinInjectionProvider<*>>,
    public val mappingProviders: MutableMap<String, MappingProvider>,
)