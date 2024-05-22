package net.yakclient.components.extloader.extension.partition

import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionMetadata
import net.yakclient.components.extloader.api.extension.partition.PartitionLoaderHelper

internal fun noMainPartition(metadata: ExtensionPartitionMetadata, helper: PartitionLoaderHelper) : Nothing {
    throw PartitionLoadException(
        metadata.name,
        "this extension has no main partition."
    ) {
        helper.partitions.keys.map { it.name } asContext "Partitions"

        solution("Defining a main partition in your ERM.")
        solution("Removing all partitions reliant on main.")
    }
}