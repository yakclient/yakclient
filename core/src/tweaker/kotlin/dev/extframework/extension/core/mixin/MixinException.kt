package dev.extframework.extension.core.mixin

import dev.extframework.extension.core.exception.CoreExceptions
import dev.extframework.tooling.api.exception.ExceptionConfiguration
import dev.extframework.tooling.api.exception.StructuredException

public fun MixinException(
    cause: Throwable? = null,
    message: String? = null,
    configure: ExceptionConfiguration.() -> Unit,
): Throwable = StructuredException(CoreExceptions.MixinException, cause, message, configure)