package net.yakclient.components.extloader.extension

import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.loader.ArchiveResourceProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.extension.versioning.ExtensionSourceProvider

public open class ExtensionClassLoader(
    archive: ExtensionArchiveReference,
    accessTree: ArchiveAccessTree,
    parent: ClassLoader,
) : IntegratedLoader(
    name = "Extension ${archive.erm.name}",
    classProvider = DelegatingClassProvider(
        accessTree.targets
            .map { it.relationship.classes }
    ),
    sourceProvider = ExtensionSourceProvider(archive),
    resourceProvider = ArchiveResourceProvider(archive),
    parent = parent
) {
    override fun loadClass(name: String): Class<*> {
        return runCatching(ClassNotFoundException::class) {
            super.loadClass(name)
        } ?: throw ClassNotFoundException(name)
    }
}
