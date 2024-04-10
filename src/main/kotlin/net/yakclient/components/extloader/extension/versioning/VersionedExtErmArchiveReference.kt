package net.yakclient.components.extloader.extension.versioning

//internal class VersionedExtErmArchiveReference(
//    override val delegate: ArchiveReference,
//    private val mcVersion: String,
//    override val erm: ExtensionRuntimeModel,
//) : ExtensionArchiveReference {
//    override val isClosed: Boolean by delegate::isClosed
//    override val location: URI by delegate::location
//    override val modified: Boolean by delegate::modified
//    override val name: String = erm.name
//    override val writer: ArchiveReference.Writer by delegate::writer
//    override val enabledPartitions: Set<ExtensionVersionPartition> =
//        erm.versionPartitions.filterTo(HashSet()) { it.supportedVersions.contains(mcVersion) }
//    override val mainPartition: MainVersionPartition = erm.mainPartition
//    override val reader: ExtensionArchiveReference.ExtensionArchiveReader = VersioningAwareReader(delegate.reader)
//
//    private val writablePartition = WriteablePartition()
//    private val allEnabledPartitions: Set<ExtensionPartition> =
//        enabledPartitions + mainPartition + WriteablePartition() //+ (erm.tweaker?.let { listOf() } ?: listOf<ExtensionPartition>())
//
////    private val entryToPartition = HashMap<String, ExtensionPartition>()
//
//    override fun close() {
//        delegate.close()
//    }
//
//    private inner class VersioningAwareWriter(
//        private val delegate: ArchiveReference.Writer
//    ) : ArchiveReference.Writer {
//        override fun put(entry: ArchiveReference.Entry) {
//            delegate.put(entry.copy(name = "${writablePartition.path}/${entry.name}"))
//        }
//
//        override fun remove(name: String) {
//            val realName = allEnabledPartitions.firstNotNullOfOrNull { p ->
//                val prefix = p.path.takeIf { it.isNotBlank() }
//                    ?.removeSuffix("/")?.plus("/") ?: ""
//                this@VersionedExtErmArchiveReference.delegate.reader["$prefix$name"]
//            }?.name ?: return
//
//            delegate.remove(realName)
//        }
//    }
//
//    private class WriteablePartition : ExtensionPartition {
//        override val name: String = "writeable"
//        override val path: String = UUID.randomUUID().toString()
//        override val repositories: List<ExtensionRepository> = listOf()
//        override val dependencies: List<Map<String, String>> = listOf()
//    }
//
//    private inner class VersioningAwareReader(
//        val delegate: ArchiveReference.Reader
//    ) : ExtensionArchiveReference.ExtensionArchiveReader {
//        private val allPartitions = erm.versionPartitions + erm.mainPartition
//
//
//        override fun determinePartition(entry: ArchiveReference.Entry): List<ExtensionPartition> {
//            return allEnabledPartitions.filter {
//                delegate.contains((it.path.takeUnless(String::isBlank)?.removeSuffix("/")?.plus("/") ?: "") + entry.name)
//            }
//        }
//
//        override fun entriesIn(partition: ExtensionPartition): Sequence<ArchiveReference.Entry> {
//            if (!enabledPartitions.contains(partition)) return emptySequence()
//
//            return delegate.entries()
//                .mapNotNull { e ->
//                    if (!e.name.startsWith(partition.path)) return@mapNotNull null
//
//                    e.copy(
//                        name = e.name.removePrefix(partition.path).removePrefix("/")
//                    )
//                }
//        }
//        // Move main partition to back of the list and dont want to access the tweaker partition
//
////        override fun determinePartition(entry: ArchiveReference.Entry): ExtensionPartition? {
////            check(entry.handle == this@VersionedExtErmArchiveReference) { "This entry does not belong to this archive!" }
////
////            return entryToPartition[entry.name]
//////            return partitions.find {
//////                entry.name.startsWith(it.path)
//////            }
////        }
////
////        override fun entriesIn(partition: String): Sequence<ArchiveReference.Entry> {
////            val path = partitions.find { it.name == partition }?.path ?: return emptySequence()
////
////            return delegate.entries()
////                .filter { it.name.startsWith(path) }
////        }
//
//        override fun entries(): Sequence<ArchiveReference.Entry> {
//            return delegate.entries()
//                .mapNotNull { e ->
//                    val splitPath = e.name.split("/")
//                    // Find the partition which path has longest match with the name of this
//                    // entry
//                    val partition = allPartitions.maxByOrNull {
//                        it.path.split("/").zip(splitPath)
//                            .takeWhile { (f, s) -> f == s }
//                            .count()
//                    } ?: return@mapNotNull null
//
//                    // Make sure that partition is enabled, and that the match is a full one.
//                    if (!enabledPartitions.contains(partition) || !e.name.startsWith(partition.path)) return@mapNotNull null
//
//                    // Copy and return
//                    e.copy(
//                        name = e.name.removePrefix(partition.path).removePrefix("/")
//                    )
//                }
//        }
//
//        override fun of(name: String): ArchiveReference.Entry? {
//            return allEnabledPartitions.firstNotNullOfOrNull { p ->
//                val prefix = p.path.takeIf { it.isNotBlank() }
//                    ?.removeSuffix("/")?.plus("/") ?: ""
//                delegate["$prefix$name"]
//            }?.copy(name = name)
//        }
//    }
//}