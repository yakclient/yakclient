package net.yakclient.plugins.yakclient.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerInfo

public data class ExtensionInfo(
    public val archive: ArchiveReference,
    public val children: List<Container<ExtensionProcess>>,
    public val dependencies: List<ArchiveHandle>,
    public val erm: ExtensionRuntimeModel
) : ContainerInfo