package net.yakclient.components.extloader.mixin

import com.durganmcbroom.resources.Resource

public open class MixinException(
    override val cause: Throwable? = null,
    override val message: String? = null
) : Exception() {
}