package dev.extframework.extloader.extension

import dev.extframework.extloader.exception.ExtLoaderExceptions
import dev.extframework.internal.api.exception.ExceptionConfiguration
import dev.extframework.internal.api.exception.StructuredException
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor

public fun ExtensionLoadException(
    descriptor: ExtensionDescriptor,
    cause: Throwable? = null,
    message: String = "Error loading extension: '$descriptor'",
    configure: ExceptionConfiguration.() -> Unit = {},
): Throwable = StructuredException(ExtLoaderExceptions.ExtensionLoadException, cause, message) {
    configure()
}