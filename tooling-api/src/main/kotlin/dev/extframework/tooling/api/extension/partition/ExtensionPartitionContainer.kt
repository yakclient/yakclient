package dev.extframework.tooling.api.extension.partition

import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor

public interface ExtensionPartitionContainer<out T : ExtensionPartition,  out M : ExtensionPartitionMetadata> : ClassLoadedArchiveNode<PartitionDescriptor> {
    override val descriptor: PartitionDescriptor
    public val metadata: M
    public val node: T

    override val handle: ArchiveHandle?
        get() = node.archive
    override val access: PartitionAccessTree
        get() = node.access
}

//public interface TargetRequiringPartitionContainer<T : ExtensionPartition, M : ExtensionPartitionMetadata> :
//    ExtensionPartitionContainer<T, M> {
//    public fun setup(linker: TargetLinker): Job<Unit>
//}

public fun <T : ExtensionPartition, M : ExtensionPartitionMetadata> ExtensionPartitionContainer(
    descriptor: PartitionDescriptor,
    metadata: M,
    node: T,
): ExtensionPartitionContainer<T, M> =
    object : ExtensionPartitionContainer<T, M> {
        override val descriptor: PartitionDescriptor = descriptor
        override val metadata: M = metadata
        override val node: T = node
    }

//internal fun <T : ExtensionPartition, M : ExtensionPartitionMetadata> ExtensionPartitionContainer(
//    descriptor: PartitionDescriptor,
//    metadata: M,
//    node: (linker: TargetLinker) -> T,
//): TargetRequiringPartitionContainer<T, M> =
//    object : TargetRequiringPartitionContainer<T, M> {
//        override val descriptor: PartitionDescriptor = descriptor
//        override val metadata: M = metadata
//
//        private var nodeInternal: T? = null
//        override val node: T
//            get() = nodeInternal
//                ?: throw IllegalArgumentException("The partition: '${metadata.name}' has not been setup yet!")
//
//        override fun setup(linker: TargetLinker): Job<Unit> = job {
//            if (nodeInternal == null) {
//                nodeInternal = node(linker)
//            }
//        }
//    }