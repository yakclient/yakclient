package dev.extframework.internal.api.extension

import dev.extframework.archives.ArchiveReference
import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey
import dev.extframework.internal.api.extension.partition.ExtensionPartitionContainer

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