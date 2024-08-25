package dev.extframework.internal.api.extension.partition

import dev.extframework.internal.api.exception.ExceptionConfiguration
import dev.extframework.internal.api.exception.InternalExceptions
import dev.extframework.internal.api.exception.StructuredException

public fun PartitionLoadException(
    partition: String,
    message: String,
    cause: Throwable? = null,
    configure: ExceptionConfiguration.() -> Unit = {},
): StructuredException = StructuredException(
    InternalExceptions.PartitionLoadException,
    cause,
    "Error loading partition '$partition' because $message"
) {
    partition asContext "Partition name"
    configure()
}