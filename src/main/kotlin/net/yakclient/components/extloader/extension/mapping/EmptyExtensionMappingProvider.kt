package net.yakclient.components.extloader.extension.mapping

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritancePath
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.internal.api.mapping.MappingsProvider

public class EmptyExtensionMappingProvider : MappingsProvider {
    override val type: String = EMPTY_MAPPING_TYPE


    public companion object {
        public const val EMPTY_MAPPING_TYPE: String = "none"
    }

    override fun forIdentifier(identifier: String): ArchiveMapping {
        return ArchiveMapping(HashMap())
    }
//    public override fun newMapper(identifier: String): MappingsProvider.Mapper {
//        return EmptyMapper(identifier)
//    }
//
//    private class EmptyMapper(
//            override val identifier: String
//    ) : MappingsProvider.Mapper {
//        override val mappings: ArchiveMapping = ArchiveMapping(HashMap())
//
//        override fun inheritancePathFor(entry: ArchiveReference.Entry, minecraft: ClassInheritanceTree): ClassInheritancePath {
//            return createInheritancePath(mappings, entry, minecraft)
//        }
//
//        override fun makeTransformer(tree: ClassInheritanceTree): TransformerConfig {
//            return TransformerConfig.of {  }
//        }
//    }
}