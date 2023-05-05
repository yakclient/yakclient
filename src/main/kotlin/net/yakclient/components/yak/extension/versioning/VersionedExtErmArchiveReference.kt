package net.yakclient.components.yak.extension.versioning

import net.yakclient.archives.ArchiveReference
import net.yakclient.components.yak.extension.ExtensionRuntimeModel
import net.yakclient.components.yak.extension.ExtensionVersionPartition
import net.yakclient.components.yak.extension.archive.ExtensionArchiveReference
import java.net.URI

internal class VersionedExtErmArchiveReference(
    override val delegate: ArchiveReference,
    override val enabledPartitions: Set<ExtensionVersionPartition>,
    override val erm: ExtensionRuntimeModel,
) : ExtensionArchiveReference {
    override val isClosed: Boolean by delegate::isClosed
    override val location: URI by delegate::location
    override val modified: Boolean by delegate::modified
    override val name: String? by delegate::name
    override val reader: ArchiveReference.Reader = VersioningAwareReader(delegate.reader)
    override val writer: ArchiveReference.Writer by delegate::writer

    override fun close() {
        delegate.close()
    }

    private inner class VersioningAwareReader(
        val delegate: ArchiveReference.Reader
    ) : ArchiveReference.Reader by delegate {
        override fun entries(): Sequence<ArchiveReference.Entry> {
            return delegate.entries()
                .map(ArchiveReference.Entry::name)
                .mapNotNull(::of)
        }

        override fun of(name: String): ArchiveReference.Entry? {
            return erm.versionPartitions.firstNotNullOfOrNull {
                delegate["${it.path.removeSuffix("/")}/$name"]
            } ?: delegate[name]
        }

        override operator fun get(name: String): ArchiveReference.Entry? = of(name)
    }
}