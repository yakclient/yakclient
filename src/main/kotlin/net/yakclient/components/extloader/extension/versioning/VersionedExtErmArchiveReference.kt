package net.yakclient.components.extloader.extension.versioning

import net.yakclient.archives.ArchiveReference
import net.yakclient.internal.api.extension.ExtensionRuntimeModel
import net.yakclient.internal.api.extension.ExtensionVersionPartition
import net.yakclient.internal.api.extension.archive.ExtensionArchiveReference
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
    override val mainPartition = erm.versionPartitions.find { it.name == erm.mainPartition } ?: throw IllegalArgumentException("Failed to find main partition: '${erm.mainPartition}' in extension: '${erm.groupId}:${erm.name}:${erm.version}'")
    override val reader: ExtensionArchiveReference.ExtensionArchiveReader = VersioningAwareReader(delegate.reader)

    override fun close() {
        delegate.close()
    }

    private inner class VersioningAwareReader(
        private val delegate: ArchiveReference.Reader
    ) : ExtensionArchiveReference.ExtensionArchiveReader {
        // Move main partition to back of the list
        private val partitions = enabledPartitions + mainPartition

        override fun determinePartition(entry: ArchiveReference.Entry): ExtensionVersionPartition? {
            check(entry.handle == this@VersionedExtErmArchiveReference) {"This entry does not belong to this archive!"}

            return partitions.find {
                entry.name.startsWith(it.path)
            }
        }

        override fun entriesIn(partition: String) : Sequence<ArchiveReference.Entry> {
            val path = partitions.find { it.name == partition }?.path ?: return emptySequence()

            return delegate.entries()
                    .filter { it.name.startsWith(path) }
        }

        override fun entries(): Sequence<ArchiveReference.Entry> {
            return delegate.entries()
                    .filter {
                        partitions.any { p -> it.name.contains(p.path) }
                    }
        }

        override fun of(name: String): ArchiveReference.Entry? {
            return partitions.firstNotNullOfOrNull {
                delegate["${it.path.removeSuffix("/")}/$name"]
            }
        }

        override operator fun get(name: String): ArchiveReference.Entry? = of(name)
    }
}