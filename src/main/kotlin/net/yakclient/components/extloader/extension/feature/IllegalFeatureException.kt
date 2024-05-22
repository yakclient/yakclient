package net.yakclient.components.extloader.extension.feature

import net.yakclient.components.extloader.api.exception.StructuredException
import net.yakclient.components.extloader.api.exception.ExceptionConfiguration
import net.yakclient.components.extloader.exception.ExtLoaderExceptions

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