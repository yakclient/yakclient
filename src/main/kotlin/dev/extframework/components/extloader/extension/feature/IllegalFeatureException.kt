package dev.extframework.components.extloader.extension.feature

import dev.extframework.components.extloader.api.exception.StructuredException
import dev.extframework.components.extloader.api.exception.ExceptionConfiguration
import dev.extframework.components.extloader.exception.ExtLoaderExceptions

//public class IllegalFeatureException(
//    override val message: String,
//    override val type: ExceptionType,
//    configure: JobExceptionContextScope.() -> Unit,
//) : JobException(configure) {
//}

public fun IllegalFeatureException(
    message: String,
    configure: ExceptionConfiguration.() -> Unit = {},
): Throwable = StructuredException(ExtLoaderExceptions.IllegalFeatureException)