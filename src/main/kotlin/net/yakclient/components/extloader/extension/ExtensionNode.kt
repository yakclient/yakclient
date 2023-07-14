package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.container.Container
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.components.extloader.extension.archive.ExtensionArchiveReference

public data class ExtensionNode(
    public val archiveReference: ExtensionArchiveReference?,
    override val children: Set<ExtensionNode>,
    public val dependencies: Set<DependencyNode>,
    public val extension: Container<ExtensionProcess>?,
    public val erm: ExtensionRuntimeModel,
) : ArchiveNode {
    override val archive: ArchiveHandle?
        get() = extension?.process?.archive

}
