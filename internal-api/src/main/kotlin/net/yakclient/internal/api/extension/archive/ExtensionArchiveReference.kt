package net.yakclient.internal.api.extension.archive

import net.yakclient.archives.ArchiveReference
import net.yakclient.internal.api.extension.ExtensionRuntimeModel
import net.yakclient.internal.api.extension.ExtensionVersionPartition

public interface ExtensionArchiveReference : ArchiveReference {
    public val delegate: ArchiveReference
    public val enabledPartitions: Set<ExtensionVersionPartition>
    public val mainPartition :ExtensionVersionPartition
    public val erm: ExtensionRuntimeModel
    override val reader: ExtensionArchiveReader

    public interface ExtensionArchiveReader : ArchiveReference.Reader {
        public fun determinePartition(entry: ArchiveReference.Entry) : ExtensionVersionPartition?

        public fun entriesIn(partition: String) : Sequence<ArchiveReference.Entry>
    }
}