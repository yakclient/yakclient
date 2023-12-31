package net.yakclient.components.extloader.api.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.extension.ExtensionClassLoader
import net.yakclient.components.extloader.extension.ExtensionProcess
import net.yakclient.components.extloader.target.TargetLinker

public interface ExtensionClassLoaderProvider : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<ExtensionClassLoaderProvider>
        get() = ExtensionClassLoaderProvider

    public fun createFor(
        archive: ExtensionArchiveReference,
        dependencies: List<ArchiveHandle>,
        manager: PrivilegeManager,
        handle: ContainerHandle<ExtensionProcess>,
        linker: TargetLinker,
        parent: ClassLoader,
    ): ClassLoader {
        return ExtensionClassLoader(
            archive, dependencies, manager, parent, handle, linker
        )
    }

    public companion object : EnvironmentAttributeKey<ExtensionClassLoaderProvider>
}