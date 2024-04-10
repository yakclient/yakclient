package net.yakclient.components.extloader.api.extension.archive

import net.yakclient.archives.ArchiveReference
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionNode

//public interface ExtensionArchiveReference : ArchiveReference {
//    public val delegate: ArchiveReference
////    public val enabledPartitions: Set<ExtensionVersionPartition>
////    public val mainPartition : MainVersionPartition
//    public val erm: ExtensionRuntimeModel
////    public val partitions: List<ExtensionPartitionNode>
//
////    public val enabledPartitions: List<ExtensionPartitionNode>
////        get() = partitions.filter(ExtensionPartitionNode::enabled)
////    override val reader: ExtensionArchiveReader
////
////    public interface ExtensionArchiveReader : ArchiveReference.Reader {
////        public fun determinePartition(entry: ArchiveReference.Entry) : List<ExtensionPartition>
////
////        public fun entriesIn(partition: ExtensionPartition) : Sequence<ArchiveReference.Entry>
////    }
//}