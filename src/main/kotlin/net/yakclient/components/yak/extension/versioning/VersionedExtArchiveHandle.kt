package net.yakclient.components.yak.extension.versioning

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.loader.ClassProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.components.yak.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.yak.extension.archive.ExtensionArchiveReference

internal class VersionedExtArchiveHandle(
    private val delegate: ArchiveHandle,
    override val reference: ExtensionArchiveReference
) : ExtensionArchiveHandle {
    override val classloader: ClassLoader by delegate::classloader
    override val name: String? by delegate::name
    override val packages: Set<String> by delegate::packages
    override val parents: Set<ArchiveHandle> by delegate::parents
}