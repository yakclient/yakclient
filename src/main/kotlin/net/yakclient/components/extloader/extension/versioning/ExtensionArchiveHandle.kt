package net.yakclient.components.extloader.extension.versioning

import net.yakclient.archives.ArchiveHandle
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveHandle

internal class  ExtensionArchiveHandle(
    override val classloader: ClassLoader,
    override val name: String?,
    override val parents: Set<ArchiveHandle>,
    override val packages: Set<String>
) : ExtensionArchiveHandle