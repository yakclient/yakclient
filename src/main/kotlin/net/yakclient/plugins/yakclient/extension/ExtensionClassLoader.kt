package net.yakclient.plugins.yakclient.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.security.SecureSourceDefiner

public fun ExtensionClassLoader(
    archive: ArchiveReference,
    dependencies: List<ArchiveHandle>,
    manager: PrivilegeManager,
    parent: ClassLoader
): ClassLoader = IntegratedLoader(
    cp = dependencies.map(::ArchiveClassProvider).let(::DelegatingClassProvider),
    sp = ArchiveSourceProvider(archive),
    sd = SecureSourceDefiner(manager),
    parent = parent
)