package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.archive.ArchiveNodeResolver
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor


public data class ExtensionNode(
    override val descriptor: ExtensionDescriptor,
    public val extensionReference: ExtensionArchiveReference?,
    override val parents: Set<ExtensionNode>,
    public val dependencies: Set<DependencyNode<*>>,
    public val container: ExtensionContainer?,
    public val erm: ExtensionRuntimeModel,
    override val access: ArchiveAccessTree,
    override val resolver: ArchiveNodeResolver<*, *, ExtensionNode, *, *>,
) : ArchiveNode<ExtensionNode> {
    //    override val archive: ArchiveHandle?
//        get() = extension?.process?.archive
    override val archive: ArchiveHandle?
        get() = container?.archive
}