package net.yakclient.components.yak.extension

import net.yakclient.boot.component.ComponentContext
import net.yakclient.components.yak.YakContext

public data class ExtensionContext(
    val parent: ComponentContext,

    val yakContext: YakContext
//    val mixinHandler: ExtensionMixinHandler,
)