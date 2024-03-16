package net.yakclient.components.extloader.api.extension

import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.extension.ExtensionClassLoader

public interface ExtensionClassLoaderProvider : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<ExtensionClassLoaderProvider>
        get() = ExtensionClassLoaderProvider

    public fun createFor(
        archive: ExtensionArchiveReference,
//        dependencies: List<ArchiveHandle>,
        accessTree: ArchiveAccessTree,
//        linker: TargetLinker,
        parent: ClassLoader,
    ): ClassLoader {
        return ExtensionClassLoader(
            archive, accessTree, parent
        )
    }

    public companion object : EnvironmentAttributeKey<ExtensionClassLoaderProvider>
}