package dev.extframework.components.extloader.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.components.extloader.api.extension.ExtensionRuntimeModel
import dev.extframework.components.extloader.api.extension.archive.ExtensionArchiveHandle
import dev.extframework.components.extloader.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.components.extloader.extension.artifact.ExtensionDescriptor


public data class ExtensionNode(
    override val descriptor: ExtensionDescriptor,
    public val container: ExtensionContainer?,
    public val partitions: List<ExtensionPartitionContainer<*, *>>,
    public val erm: ExtensionRuntimeModel,
    override val parents: Set<ExtensionNode>,
    override val resolver: ArchiveNodeResolver<*, *, ExtensionNode, *, *>,
) : ArchiveNode<ExtensionNode> {
    override val archive: ExtensionArchiveHandle?
        get() = container?.archive

    override val access: ArchiveAccessTree = object : ArchiveAccessTree {
        override val descriptor: ArtifactMetadata.Descriptor = this@ExtensionNode.descriptor
        override val targets: List<ArchiveTarget> = listOf()
    }
}