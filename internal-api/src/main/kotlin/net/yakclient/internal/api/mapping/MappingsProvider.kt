package net.yakclient.internal.api.mapping

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritancePath
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.transform.TransformerConfig

public interface MappingsProvider {
    public val type: String

    public fun forIdentifier(identifier: String): ArchiveMapping

//    public interface Mapper {
//        public val identifier: String
//        public val mappings: ArchiveMapping
//
//        // The obfuscated inheritance tree of minecraft
//        public fun inheritancePathFor(
//                entry: ArchiveReference.Entry,
//                minecraft: ClassInheritanceTree
//        ) : ClassInheritancePath
//
//        // The class inheritance tree (a map in java) will be whatever is provided above ^
//        public fun makeTransformer(
//                tree: ClassInheritanceTree,
//        ): TransformerConfig
//    }
}