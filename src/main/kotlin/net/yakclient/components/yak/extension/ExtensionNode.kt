package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.container.Container
import net.yakclient.boot.dependency.DependencyNode

public data class ExtensionNode(
    override val archive: ArchiveHandle?,
    override val children: Set<ExtensionNode>,
    public val dependencies: Set<DependencyNode>,
    public val extension: Container<ExtensionProcess>?,
    public val runtimeModel: ExtensionRuntimeModel
) : ArchiveNode
