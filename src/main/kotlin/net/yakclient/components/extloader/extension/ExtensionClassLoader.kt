package net.yakclient.components.extloader.extension

import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionNode

public open class ExtensionClassLoader(
    name: String,
    partitions: List<ExtensionPartitionNode>,
    parent: ClassLoader,
) : IntegratedLoader(
    name = "Extension $name",
    classProvider = DelegatingClassProvider(
        partitions.map { it.archive }.map(::ArchiveClassProvider)
    ),
    parent = parent
) {
    override fun loadClass(name: String): Class<*> {
        return runCatching(ClassNotFoundException::class) {
            super.loadClass(name)
        } ?: throw ClassNotFoundException(name)
    }
}
