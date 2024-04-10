package net.yakclient.components.extloader.extension

// Not documented on ContainerInfo, but this is purely data for loading ExtensionsProcesses, it is only persisted in memory as long as that loading takes place
//public data class ExtensionInfo(
//    public val archive: ExtensionArchiveReference,
//    public val children: List<ArchiveContainer>,
//    public val dependencies: List<ArchiveContainer>,
//    public val erm: ExtensionRuntimeModel,
//    public val handle: ContainerHandle,
//    override val access: ArchiveAccessTree
//) : ArchiveContainerInfo