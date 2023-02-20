package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.container.Container
import net.yakclient.boot.dependency.DependencyNode

public data class ExtensionNode(
    public val archiveReference: ArchiveReference?,
    override val children: Set<ExtensionNode>,
    public val dependencies: Set<DependencyNode>,
    public val extension: Container<ExtensionProcess>?,
    public val extensionMetadata: ExtensionMetadata,
) : ArchiveNode {
    override val archive: ArchiveHandle?
        get() = extension?.process?.archive

}
