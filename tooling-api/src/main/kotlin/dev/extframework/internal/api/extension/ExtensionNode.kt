package dev.extframework.internal.api.extension

import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.partition.ExtensionPartitionContainer

public class ExtensionNode(
    override val descriptor: ExtensionDescriptor,
    override val access: ArchiveAccessTree,

    public val parents: List<ExtensionNode>,
    public val classLoader: ExtensionClassLoader,

    public val runtimeModel: ExtensionRuntimeModel
) : ArchiveNode<ExtensionDescriptor> {
    public val partitions: List<ExtensionPartitionContainer<*, *>> by classLoader::partitions
}