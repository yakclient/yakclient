package net.yakclient.components.extloader.extension.partition

import net.yakclient.components.extloader.api.exception.StructuredException
import net.yakclient.components.extloader.api.exception.ExceptionConfiguration
import net.yakclient.components.extloader.exception.ExtLoaderExceptions

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