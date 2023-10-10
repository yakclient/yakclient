package net.yakclient.components.extloader.extension.versioning

import net.yakclient.archives.ArchiveHandle
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference

internal class  VersionedExtArchiveHandle(
//    private val delegate: ArchiveHandle,
    override val reference: ExtensionArchiveReference,
    override val classloader: ClassLoader,
    override val name: String?,
    override val parents: Set<ArchiveHandle>,
    override val packages: Set<String>
) : ExtensionArchiveHandle {
//    override val classloader: ClassLoader by delegate::classloader
//    override val name: String? by delegate::name
//    override val packages: Set<String> by delegate::packages
//    override val parents: Set<ArchiveHandle> by delegate::parents
}