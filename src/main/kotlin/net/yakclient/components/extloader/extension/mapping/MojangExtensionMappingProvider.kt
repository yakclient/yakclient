package net.yakclient.components.extloader.extension.mapping

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.parsers.ProGuardMappingParser
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.internal.api.mapping.MappingsProvider
import net.yakclient.launchermeta.handler.clientMappings
import net.yakclient.launchermeta.handler.loadVersionManifest
import net.yakclient.launchermeta.handler.metadata
import net.yakclient.launchermeta.handler.parseMetadata
import org.objectweb.asm.Handle
import org.objectweb.asm.tree.*
import java.nio.file.Path

public class MojangExtensionMappingProvider(
        private val mappingStore: DataStore<String, SafeResource>
) : MappingsProvider {
    override val type: String = MOJANG_MAPPING_TYPE

    public companion object {
        public const val MOJANG_MAPPING_TYPE: String = "mojang"
    }

    public constructor(path: Path) : this(CachingDataStore(MojangMappingAccess(path)))

    override fun forIdentifier(identifier: String): ArchiveMapping {
        val mappingData = mappingStore[identifier] ?: run {
            val manifest = loadVersionManifest()
            val version = manifest.find(identifier)
                    ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
            val m = parseMetadata(version.metadata()).clientMappings()
            mappingStore.put(identifier, m)
            m
        }

        return  ProGuardMappingParser.parse(mappingData.open())

//        return MojangExtensionMapper(
//                identifier,
//                mappings,
//        )
    }

//    private class MojangExtensionMapper(
//            override val identifier: String,
//            override val mappings: ArchiveMapping
//    ) : MappingsProvider.Mapper {
//        override fun makeTransformer(
//                tree: ClassInheritanceTree,
//        ): TransformerConfig {
//            fun ClassInheritancePath.toCheck(): List<String> {
//                return listOf(name) + interfaces.flatMap { it.toCheck() } + (superClass?.toCheck() ?: listOf())
//            }
//
//            return TransformerConfig.of {
//                transformClass { classNode: ClassNode ->
//                    mappings.run {
//                        val direction = MappingDirection.TO_FAKE
//                        for (methodNode in classNode.methods) {
//                            methodNode.instructions.forEach { insnNode ->
//                                when (insnNode) {
//                                    is FieldInsnNode -> {
//                                        insnNode.name = tree[insnNode.owner]?.toCheck()?.firstNotNullOfOrNull {
//                                            mapFieldName(
//                                                    it,
//                                                    insnNode.name,
//                                                    direction
//                                            )
//                                        } ?: insnNode.name
//
//                                        insnNode.owner = mapClassName(insnNode.owner, direction) ?: insnNode.owner
//                                        insnNode.desc = mapType(insnNode.desc, direction)
//                                    }
//
//                                    is InvokeDynamicInsnNode -> {
//                                        fun Handle.mapHandle(): Handle = Handle(
//                                                tag,
//                                                mapType(insnNode.bsm.owner, direction),
//                                                tree[insnNode.bsm.owner]?.toCheck()?.firstNotNullOfOrNull {
//                                                    mapMethodName(
//                                                            it,
//                                                            name,
//                                                            desc,
//                                                            direction
//                                                    )
//                                                } ?: name,
//                                                mapMethodDesc(desc, direction),
//                                                isInterface
//                                        )
//
//                                        // Type and Handle
//                                        insnNode.bsm = insnNode.bsm.mapHandle()
//
//
//                                        // Can ignore name because only the name of the bootstrap method is known at compile time and that is held in the handle field
//                                        insnNode.desc =
//                                                mapMethodDesc(
//                                                        insnNode.desc,
//                                                        direction
//                                                ) // Expected descriptor type of the generated call site
//                                    }
//
//                                    is MethodInsnNode -> {
//                                        insnNode.name = tree[insnNode.owner]?.toCheck()?.firstNotNullOfOrNull {
//                                            mapMethodName(
//                                                    it,
//                                                    insnNode.name,
//                                                    insnNode.desc,
//                                                    direction
//                                            )
//                                        } ?: insnNode.name
//
//                                        insnNode.owner = mapClassName(insnNode.owner, direction) ?: insnNode.owner
//                                        insnNode.desc = mapMethodDesc(insnNode.desc, direction)
//                                    }
//
//                                    is MultiANewArrayInsnNode -> {
//                                        insnNode.desc = mapType(insnNode.desc, direction)
//                                    }
//
//                                    is TypeInsnNode -> {
//                                        insnNode.desc = mapClassName(insnNode.desc, direction) ?: insnNode.desc
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        override fun inheritancePathFor(entry: ArchiveReference.Entry, minecraft: ClassInheritanceTree): ClassInheritancePath {
//            return createInheritancePath(mappings, entry, minecraft)
//        }
//    }
}