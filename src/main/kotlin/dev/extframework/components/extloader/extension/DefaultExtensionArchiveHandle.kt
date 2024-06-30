package dev.extframework.components.extloader.extension

import dev.extframework.archives.ArchiveHandle
import dev.extframework.components.extloader.api.extension.archive.ExtensionArchiveHandle

internal class DefaultExtensionArchiveHandle(
    override val classloader: ClassLoader,
    override val name: String?,
    override val parents: Set<ArchiveHandle>,
    override val packages: Set<String>
) : ExtensionArchiveHandle