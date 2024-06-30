package dev.extframework.components.extloader.extension.partition

import dev.extframework.components.extloader.api.exception.StructuredException
import dev.extframework.components.extloader.api.exception.ExceptionConfiguration
import dev.extframework.components.extloader.exception.ExtLoaderExceptions

internal fun PartitionLoadException(
    partition: String,
    message: String,
    cause: Throwable? = null,
    configure: ExceptionConfiguration.() -> Unit = {},
) = StructuredException(
    ExtLoaderExceptions.PartitionLoadException,
    cause,
    "Error loading partition '$partition' because $message"
) {
    partition asContext "Partition name"
    configure()
}