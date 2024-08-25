package dev.extframework.extension.core.partition

import dev.extframework.internal.api.extension.ExtensionRuntimeModel
import dev.extframework.internal.api.extension.partition.PartitionCacheHelper
import dev.extframework.internal.api.extension.partition.PartitionLoadException
import dev.extframework.internal.api.extension.partition.PartitionLoaderHelper

internal fun noMainPartition(metadata: ExtensionRuntimeModel, helper: PartitionLoaderHelper): Nothing {
    throw PartitionLoadException(
        metadata.name,
        "this extension has no main partition."
    ) {
        helper.erm.partitions.map { it.name } asContext "Partitions"

        solution("Defining a main partition in your ERM.")
        solution("Removing all partitions reliant on main.")
    }
}

internal fun noMainPartition(metadata: ExtensionRuntimeModel, helper: PartitionCacheHelper): Nothing {
    throw PartitionLoadException(
        metadata.name,
        "this extension has no main partition."
    ) {
        helper.erm.partitions.map { it.name } asContext "Partitions"

        solution("Defining a main partition in your ERM.")
        solution("Removing all partitions reliant on main.")
    }
}