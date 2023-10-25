package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.container.Container
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor

public data class ExtensionNode(
    public val descriptor: ExtensionDescriptor,
    public val archiveReference: ExtensionArchiveReference?,
    override val children: Set<ExtensionNode>,
    public val dependencies: Set<DependencyNode>,
    public val extension: Container<ExtensionProcess>?,
    public val erm: ExtensionRuntimeModel,
    public val tweakerHandle: EnvironmentTweakerHandle?
//        public val environmentTweakers: List<EnvironmentTweakerNode>
) : ArchiveNode {
    override val archive: ArchiveHandle?
        get() = extension?.process?.archive
}

public data class EnvironmentTweakerHandle(
    val tweaker: EnvironmentTweaker,
    val handle: ArchiveHandle
)
