package net.yakclient.components.extloader.extension

import net.yakclient.components.extloader.api.exception.StructuredException
import net.yakclient.components.extloader.api.exception.ExceptionConfiguration
import net.yakclient.components.extloader.exception.ExtLoaderExceptions
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor

public fun ExtensionLoadException(
    descriptor: ExtensionDescriptor,
    cause: Throwable? = null,
    configure: ExceptionConfiguration.() -> Unit = {},
): Throwable = StructuredException(ExtLoaderExceptions.ExtensionLoadException, cause, "Error loading extension: '$descriptor'") {
    configure()
}