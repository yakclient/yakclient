package dev.extframework.components.extloader.api.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.components.extloader.target.TargetLinker

public interface ExtensionPartitionContainer<T : ExtensionPartitionNode, M : ExtensionPartitionMetadata> {
    public val descriptor: ArtifactMetadata.Descriptor
    public val metadata: M
    public val node: T
}

public interface TargetRequiringPartitionContainer<T : ExtensionPartitionNode, M : ExtensionPartitionMetadata> :
    ExtensionPartitionContainer<T, M> {
    public fun setup(linker: TargetLinker): Job<Unit>
}

internal fun <T : ExtensionPartitionNode, M : ExtensionPartitionMetadata> ExtensionPartitionContainer(
    descriptor: ArtifactMetadata.Descriptor,
    metadata: M,
    node: T,
): ExtensionPartitionContainer<T, M> =
    object : ExtensionPartitionContainer<T, M> {
        override val descriptor: ArtifactMetadata.Descriptor = descriptor
        override val metadata: M = metadata
        override val node: T = node
    }

internal fun <T : ExtensionPartitionNode, M : ExtensionPartitionMetadata> ExtensionPartitionContainer(
    descriptor: ArtifactMetadata.Descriptor,
    metadata: M,
    node: (linker: TargetLinker) -> T,
): TargetRequiringPartitionContainer<T, M> =
    object : TargetRequiringPartitionContainer<T, M> {
        override val descriptor: ArtifactMetadata.Descriptor = descriptor
        override val metadata: M = metadata

        private var nodeInternal: T? = null
        override val node: T
            get() = nodeInternal
                ?: throw IllegalArgumentException("The partition: '${metadata.name}' has not been setup yet!")

        override fun setup(linker: TargetLinker): Job<Unit> = job {
            if (nodeInternal == null) {
                nodeInternal = node(linker)
            }
        }
    }