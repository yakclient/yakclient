package dev.extframework.components.extloader.api.extension

import dev.extframework.archives.ArchiveReference
import dev.extframework.components.extloader.api.environment.EnvironmentAttribute
import dev.extframework.components.extloader.api.environment.EnvironmentAttributeKey
import dev.extframework.components.extloader.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.components.extloader.extension.ExtensionClassLoader

public interface ExtensionClassLoaderProvider : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<ExtensionClassLoaderProvider>
        get() = ExtensionClassLoaderProvider

    public fun createFor(
        archive: ArchiveReference,
        erm: ExtensionRuntimeModel,
        partitions: List<ExtensionPartitionContainer<*, *>>,
        parent: ClassLoader,
    ): ExtensionClassLoader {
        return ExtensionClassLoader(
            erm.name, partitions.toMutableList(), parent
        )
    }

    public companion object : EnvironmentAttributeKey<ExtensionClassLoaderProvider>
}