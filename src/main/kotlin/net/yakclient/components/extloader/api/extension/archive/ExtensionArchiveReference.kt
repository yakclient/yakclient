package net.yakclient.components.extloader.api.extension.archive

import net.yakclient.archives.ArchiveReference
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.ExtensionVersionPartition
import net.yakclient.components.extloader.api.extension.MainVersionPartition

public interface ExtensionArchiveReference : ArchiveReference {
    public val delegate: ArchiveReference
    public val enabledPartitions: Set<ExtensionVersionPartition>
    public val mainPartition : MainVersionPartition
    public val erm: ExtensionRuntimeModel
    override val reader: ExtensionArchiveReader

    public interface ExtensionArchiveReader : ArchiveReference.Reader {
//        public fun determinePartition(entry: ArchiveReference.Entry) : ExtensionPartition?
//
//        public fun entriesIn(partition: String) : Sequence<ArchiveReference.Entry>
    }
}