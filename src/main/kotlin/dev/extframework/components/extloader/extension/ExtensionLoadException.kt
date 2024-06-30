package dev.extframework.components.extloader.extension

import dev.extframework.components.extloader.api.exception.StructuredException
import dev.extframework.components.extloader.api.exception.ExceptionConfiguration
import dev.extframework.components.extloader.exception.ExtLoaderExceptions
import dev.extframework.components.extloader.extension.artifact.ExtensionDescriptor

public fun ExtensionLoadException(
    descriptor: ExtensionDescriptor,
    cause: Throwable? = null,
    configure: ExceptionConfiguration.() -> Unit = {},
): Throwable = StructuredException(ExtLoaderExceptions.ExtensionLoadException, cause, "Error loading extension: '$descriptor'") {
    configure()
}