package dev.extframework.internal.api.extension

import dev.extframework.boot.loader.IntegratedLoader
import dev.extframework.internal.api.extension.partition.ExtensionPartitionContainer

public open class ExtensionClassLoader(
    name: String,
    public val partitions: MutableList<ExtensionPartitionContainer<*, *>>,
    parent: ClassLoader,
) : IntegratedLoader(
    name = "Extension $name",
    parent = parent
)