package net.yakclient.components.extloader.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.archive.ArchiveNodeResolver
import net.yakclient.boot.archive.ArchiveTarget
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionContainer
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor


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