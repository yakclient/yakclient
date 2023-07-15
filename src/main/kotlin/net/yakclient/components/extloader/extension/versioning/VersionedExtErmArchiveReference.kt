package net.yakclient.components.extloader.extension.versioning

import net.yakclient.archives.ArchiveReference
import net.yakclient.components.extloader.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.extension.ExtensionVersionPartition
import net.yakclient.components.extloader.extension.archive.ExtensionArchiveReference
import java.net.URI

internal class VersionedExtErmArchiveReference(
    override val delegate: ArchiveReference,
    val mcVersion: String,
    override val erm: ExtensionRuntimeModel,
) : ExtensionArchiveReference {
    override val isClosed: Boolean by delegate::isClosed
    override val location: URI by delegate::location
    override val modified: Boolean by delegate::modified
    override val name: String? by delegate::name
    override val writer: ArchiveReference.Writer by delegate::writer
    override val enabledPartitions: Set<ExtensionVersionPartition> = erm.versionPartitions.filterTo(HashSet()) { it.supportedVersions.contains(mcVersion) && it.name != erm.mainPartition }
    val mainPartition = erm.versionPartitions.find { it.name == erm.mainPartition } ?: throw IllegalArgumentException("Failed to find main partition: '${erm.mainPartition}' in extension: '${erm.groupId}:${erm.name}:${erm.version}'")
    override val reader: ArchiveReference.Reader = VersioningAwareReader(delegate.reader)


    override fun close() {
        delegate.close()
    }

    private inner class VersioningAwareReader(
        val delegate: ArchiveReference.Reader
    ) : ArchiveReference.Reader by delegate {
        // Move main partition to back of the list
        private val partitions = enabledPartitions + mainPartition

        override fun entries(): Sequence<ArchiveReference.Entry> {
            return delegate.entries()
                .filter { partitions.any { p -> it.name.contains(p.path) } }
        }

        override fun of(name: String): ArchiveReference.Entry? {
            return partitions.firstNotNullOfOrNull {
                delegate["${it.path.removeSuffix("/")}/$name"]
            }
        }

        override operator fun get(name: String): ArchiveReference.Entry? = of(name)
    }
}