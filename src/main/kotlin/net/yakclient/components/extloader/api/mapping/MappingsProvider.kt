package net.yakclient.components.extloader.api.mapping

import net.yakclient.archive.mapper.ArchiveMapping

public interface MappingsProvider {

    // Mappings reciprocal so there is no to and from, just A and B.
    public val realType: String
    public val fakeType: String

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