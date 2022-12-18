package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.container.ContainerSource
import net.yakclient.boot.loader.*
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.security.SecureSourceDefiner
import net.yakclient.boot.security.SecuredSource
import java.security.ProtectionDomain

public fun ExtensionClassLoader(
    archive: ArchiveReference,
    dependencies: List<ArchiveHandle>,
    manager: PrivilegeManager,
    parent: ClassLoader,
    handle: ContainerHandle<ExtensionProcess>
): ClassLoader = IntegratedLoader(
    cp = dependencies.map(::ArchiveClassProvider).let(::DelegatingClassProvider),
    sp = ArchiveSourceProvider(archive),
    sd = { name, bytes, loader, definer ->
        val domain = ProtectionDomain(ContainerSource(handle), manager.permissions)

        definer(name, bytes, domain)
    },
    parent = parent
)