package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.container.ContainerSource
import net.yakclient.boot.loader.*
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.components.yak.extension.archive.ExtensionArchiveReference
import java.security.ProtectionDomain
import net.yakclient.components.yak.extension.versioning.ExtensionSourceProvider

public fun ExtensionClassLoader(
    archive: ExtensionArchiveReference,
    dependencies: List<ArchiveHandle>,
    manager: PrivilegeManager,
    parent: ClassLoader,
    handle: ContainerHandle<ExtensionProcess>,
): ClassLoader = IntegratedLoader(
    cp = dependencies.map(::ArchiveClassProvider).let(::DelegatingClassProvider),
    sp = ExtensionSourceProvider(archive),
    sd = { name, bytes, loader, definer ->
        val domain = ProtectionDomain(ContainerSource(handle), manager.permissions)

        definer(name, bytes, domain)
    },
    parent = parent
)