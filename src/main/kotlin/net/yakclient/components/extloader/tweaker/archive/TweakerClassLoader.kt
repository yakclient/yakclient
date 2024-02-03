package net.yakclient.components.extloader.tweaker.archive

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveTree
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.loader.*

internal fun TweakerClassLoader(
    ref: TweakerArchiveReference,
    accessTree: ArchiveAccessTree,
    parent: ClassLoader
): ClassLoader = IntegratedLoader(
    name = ref.name,
    sourceProvider = ArchiveSourceProvider(ref),
    classProvider = DelegatingClassProvider(accessTree.targets.map { it.relationship.classes }),
    resourceProvider = DelegatingResourceProvider(accessTree.targets.map { it.relationship.resources } + ArchiveResourceProvider(
        ref
    )),
    parent = parent
)