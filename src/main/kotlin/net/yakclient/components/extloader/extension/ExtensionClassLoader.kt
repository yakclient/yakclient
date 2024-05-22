package net.yakclient.components.extloader.extension

import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionContainer

public open class ExtensionClassLoader(
    name: String,
    public val partitions: MutableList<ExtensionPartitionContainer<*, *>>,
    parent: ClassLoader,
) : IntegratedLoader(
    name = "Extension $name",
    parent = parent
)