package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.container.ContainerSource
import net.yakclient.boot.loader.*
import net.yakclient.boot.security.PrivilegeManager
import java.security.ProtectionDomain
import net.yakclient.components.yak.extension.versioning.PartitionedVersioningSourceProvider

public fun ExtensionClassLoader(
    archive: ArchiveReference,
    dependencies: List<ArchiveHandle>,
    manager: PrivilegeManager,
    parent: ClassLoader,
    handle: ContainerHandle<ExtensionProcess>,
    activePartitions: Set<String>
): ClassLoader = IntegratedLoader(
    cp = dependencies.map(::ArchiveClassProvider).let(::DelegatingClassProvider),
    sp = PartitionedVersioningSourceProvider(activePartitions, archive),
    sd = { name, bytes, loader, definer ->
        val domain = ProtectionDomain(ContainerSource(handle), manager.permissions)

        definer(name, bytes, domain)
    },
    parent = parent
)