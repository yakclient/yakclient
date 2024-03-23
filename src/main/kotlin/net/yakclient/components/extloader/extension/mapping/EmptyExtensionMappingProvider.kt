package net.yakclient.components.extloader.extension.mapping

//public class EmptyExtensionMappingProvider : MappingsProvider {
////    override val typeFrom: String = EMPTY_MAPPING_TYPE
//
//
//    public companion object {
//        public const val EMPTY_MAPPING_TYPE: String = "none"
//    }
//
//    override val realType: String
//        get() = TODO("Not yet implemented")
//    override val fakeType: String
//        get() = TODO("Not yet implemented")
//
//    override fun forIdentifier(identifier: String): ArchiveMapping {
//        return ArchiveMapping(HashMap())
//    }
////    public override fun newMapper(identifier: String): MappingsProvider.Mapper {
////        return EmptyMapper(identifier)
////    }
////
////    private class EmptyMapper(
////            override val identifier: String
////    ) : MappingsProvider.Mapper {
////        override val mappings: ArchiveMapping = ArchiveMapping(HashMap())
////
////        override fun inheritancePathFor(entry: ArchiveReference.Entry, minecraft: ClassInheritanceTree): ClassInheritancePath {
////            return createInheritancePath(mappings, entry, minecraft)
////        }
////
////        override fun makeTransformer(tree: ClassInheritanceTree): TransformerConfig {
////            return TransformerConfig.of {  }
////        }
////    }
//}