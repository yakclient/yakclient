package dev.extframework.components.extloader.mixin

import dev.extframework.components.extloader.api.exception.StructuredException
import dev.extframework.components.extloader.api.exception.ExceptionConfiguration
import dev.extframework.components.extloader.exception.ExtLoaderExceptions

public fun MixinException(
    cause: Throwable? = null,
    message: String? = null,
    configure: ExceptionConfiguration.() -> Unit,
): Throwable = StructuredException(ExtLoaderExceptions.MixinException, cause, message, configure)