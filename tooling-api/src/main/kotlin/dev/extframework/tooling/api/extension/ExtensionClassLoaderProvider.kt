package dev.extframework.tooling.api.extension

import dev.extframework.archives.ArchiveReference
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer

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