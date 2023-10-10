package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.container.ContainerInfo
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel

// Not documented on ContainerInfo, but this is purely data for loading ExtensionsProcesses, it is only persisted in memory as long as that loading takes place
public data class ExtensionInfo(
    public val archive: ExtensionArchiveReference,
    public val children: List<Container<ExtensionProcess>>,
    public val dependencies: List<ArchiveHandle>,
    public val erm: ExtensionRuntimeModel,
    public val handle: ContainerHandle<ExtensionProcess>
) : ContainerInfo