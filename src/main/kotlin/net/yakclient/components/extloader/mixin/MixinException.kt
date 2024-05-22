package net.yakclient.components.extloader.mixin

import net.yakclient.components.extloader.api.exception.StructuredException
import net.yakclient.components.extloader.api.exception.ExceptionConfiguration
import net.yakclient.components.extloader.exception.ExtLoaderExceptions

public fun MixinException(
    cause: Throwable? = null,
    message: String? = null,
    configure: ExceptionConfiguration.() -> Unit,
): Throwable = StructuredException(ExtLoaderExceptions.MixinException, cause, message, configure)