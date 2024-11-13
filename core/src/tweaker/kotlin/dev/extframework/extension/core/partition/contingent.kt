package dev.extframework.extension.core.partition

import dev.extframework.tooling.api.extension.partition.ExtensionPartitionLoader
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionMetadata

public interface ContingentPartitionMetadata : ExtensionPartitionMetadata {
    public val enabled: Boolean
}

public interface ContingentPartitionLoader<T: ContingentPartitionMetadata> : ExtensionPartitionLoader<T>