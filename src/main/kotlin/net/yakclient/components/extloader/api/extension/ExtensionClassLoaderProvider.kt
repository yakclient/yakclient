package net.yakclient.components.extloader.api.extension

import net.yakclient.archives.ArchiveReference
import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionNode
import net.yakclient.components.extloader.extension.ExtensionClassLoader

public interface ExtensionClassLoaderProvider : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<ExtensionClassLoaderProvider>
        get() = ExtensionClassLoaderProvider

    public fun createFor(
        archive: ArchiveReference,
        erm: ExtensionRuntimeModel,
        partitions: List<ExtensionPartitionNode>,
        parent: ClassLoader,
    ): ClassLoader {
        return ExtensionClassLoader(
            erm.name, partitions, parent
        )
    }

    public companion object : EnvironmentAttributeKey<ExtensionClassLoaderProvider>
}