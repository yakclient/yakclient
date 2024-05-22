package net.yakclient.components.extloader.extension

import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionContainer

public open class ExtensionClassLoader(
    name: String,
    public val partitions: MutableList<ExtensionPartitionContainer<*, *>>,
    parent: ClassLoader,
) : IntegratedLoader(
    name = "Extension $name",
//    // TODO do we need this? For features its nice but may better to have features manually rely
//    classProvider = DelegatingClassProvider(
//        partitions.map { it.node.archive }.map(::ArchiveClassProvider)
//    ),
    parent = parent
)