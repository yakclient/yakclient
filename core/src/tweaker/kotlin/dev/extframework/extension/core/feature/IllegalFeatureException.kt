package dev.extframework.extension.core.feature

import dev.extframework.internal.api.exception.ExceptionConfiguration
import dev.extframework.internal.api.exception.InternalExceptions
import dev.extframework.internal.api.exception.StructuredException

public fun IllegalFeatureException(
    message: String,
    configure: ExceptionConfiguration.() -> Unit = {},
): Throwable =
    StructuredException(InternalExceptions.IllegalFeatureException, message = message, configure = configure)