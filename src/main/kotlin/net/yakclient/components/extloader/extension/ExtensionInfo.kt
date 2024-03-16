package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.security.ArchiveControl
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.container.*
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel

// Not documented on ContainerInfo, but this is purely data for loading ExtensionsProcesses, it is only persisted in memory as long as that loading takes place
//public data class ExtensionInfo(
//    public val archive: ExtensionArchiveReference,
//    public val children: List<ArchiveContainer>,
//    public val dependencies: List<ArchiveContainer>,
//    public val erm: ExtensionRuntimeModel,
//    public val handle: ContainerHandle,
//    override val access: ArchiveAccessTree
//) : ArchiveContainerInfo