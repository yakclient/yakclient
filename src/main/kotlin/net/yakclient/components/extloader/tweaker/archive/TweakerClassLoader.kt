package net.yakclient.components.extloader.tweaker.archive

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveTree
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader

internal fun TweakerClassLoader(
    ref: ArchiveReference,
    dependencies: Set<ArchiveHandle>,
    parent: ClassLoader
) : ClassLoader = IntegratedLoader(
    sp = ArchiveSourceProvider(ref),
    cp = DelegatingClassProvider(dependencies.map(::ArchiveClassProvider)),
    parent = parent
)