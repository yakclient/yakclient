package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.container.ContainerInfo

public data class ExtensionInfo(
    public val archive: ArchiveReference,
    public val children: List<Container<ExtensionProcess>>,
    public val dependencies: List<ArchiveHandle>,
    public val extensionMetadata: ExtensionMetadata,
    public val handle: ContainerHandle<ExtensionProcess>
) : ContainerInfo