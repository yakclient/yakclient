package dev.extframework.tooling.api.extension.partition

import dev.extframework.tooling.api.exception.ExceptionConfiguration
import dev.extframework.tooling.api.exception.InternalExceptions
import dev.extframework.tooling.api.exception.StructuredException

public fun PartitionLoadException(
    partition: String,
    message: String,
    cause: Throwable? = null,
    configure: ExceptionConfiguration.() -> Unit = {},
): StructuredException = StructuredException(
    InternalExceptions.PartitionLoadException,
    cause,
    "Error loading partition '$partition' because ${message.replaceFirstChar(Char::lowercase)}",
) {
    partition asContext "Partition name"
    configure()
}