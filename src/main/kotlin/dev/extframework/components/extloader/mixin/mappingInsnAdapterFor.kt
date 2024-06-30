package dev.extframework.components.extloader.mixin

import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.transform.*
import dev.extframework.archives.transform.InstructionAdapter
import dev.extframework.archives.transform.InstructionResolver
import dev.extframework.common.util.equalsAny
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

internal fun mappingInsnAdapterFor(
    tree: ClassInheritanceTree,
    mappings: ArchiveMapping,
    from: String,
    to: String,
    parent: InstructionResolver
) = object : InstructionAdapter(parent) {
    override fun get(): InsnList {
        val insn = parent.get()

        fun <R> ClassInheritancePath.fromTreeInternal(transform: (String) -> R?): R? {
            val mappedName = mappings.mapClassName(name, from, to) ?: name
            return transform(mappedName)
                ?: (interfaces + (listOfNotNull(superClass))).firstNotNullOfOrNull { it.fromTreeInternal(transform) }
        }

        fun <R> fromTree(start: String, transform: (String) -> R?): R? {
            return tree[mappings.mapClassName(start, from, to)]?.fromTreeInternal(transform)
        }

        mappings.run {
            insn.forEach { insnNode ->
                when (insnNode) {
                    is FieldInsnNode -> {
                        val newOwner =
                            if (insnNode.owner == from /*&& insnNode.opcode != Opcodes.GETSTATIC */) to else insnNode.owner

                        insnNode.name = fromTree(newOwner) {
                            mapFieldName(
                                it,
                                insnNode.name,
                                from, to
                            )
                        } ?: insnNode.name

                        insnNode.owner = mapClassName(newOwner, from, to) ?: newOwner
                        insnNode.desc = mapType(insnNode.desc, from, to)
                    }

                    is InvokeDynamicInsnNode -> {
                        fun Handle.mapHandle(): Handle {
                            val newOwner = if (owner == from) to else owner

                            return if (
                                tag.equalsAny(
                                    Opcodes.H_INVOKEVIRTUAL,
                                    Opcodes.H_INVOKESTATIC,
                                    Opcodes.H_INVOKESPECIAL,
                                    Opcodes.H_NEWINVOKESPECIAL,
                                    Opcodes.H_INVOKEINTERFACE
                                )
                            ) Handle(
                                tag,
                                mapClassName(newOwner, from, to) ?: newOwner,
                                fromTree(newOwner) {
                                    mapMethodName(
                                        it,
                                        name,
                                        desc,
                                        from, to
                                    )
                                } ?: name,
                                mapMethodDesc(desc, from, to),
                                isInterface
                            ) else if (
                                tag.equalsAny(
                                    Opcodes.H_GETFIELD,
                                    Opcodes.H_GETSTATIC,
                                    Opcodes.H_PUTFIELD,
                                    Opcodes.H_PUTSTATIC
                                )
                            ) Handle(
                                tag,
                                mapClassName(newOwner, from, to) ?: newOwner,
                                fromTree(newOwner) {
                                    mapFieldName(
                                        it,
                                        name,
                                        from, to
                                    )
                                } ?: name,
                                mapType(desc, from, to),
                                isInterface
                            ) else throw IllegalArgumentException("Unknown tag type : '$tag' for invoke dynamic instruction : '$insnNode' with handle: '$this'")
                        }

                        // Type and Handle
                        insnNode.bsm = insnNode.bsm.mapHandle()

                        insnNode.bsmArgs = insnNode.bsmArgs.map {
                            when (it) {
                                is Type -> {
                                    when (it.sort) {
                                        Type.ARRAY, Type.OBJECT -> Type.getType(mapType(it.internalName, from, to))
                                        Type.METHOD -> Type.getType(mapMethodDesc(it.internalName, from, to))
                                        else -> it
                                    }
                                }

                                is Handle -> it.mapHandle()
                                else -> it
                            }
                        }.toTypedArray()


                        // Can ignore name because only the name of the bootstrap method is known at compile time and that is held in the handle field
                        insnNode.desc =
                            mapMethodDesc(
                                insnNode.desc,
                                from, to
                            ) // Expected descriptor type of the generated call site
                    }

                    is MethodInsnNode -> {
                        val newOwner =
                            if (insnNode.owner == from /*&& insnNode.opcode != Opcodes.INVOKESTATICf */) to else insnNode.owner

                        insnNode.name = fromTree(newOwner) {
                            mapMethodName(
                                it,
                                insnNode.name,
                                insnNode.desc,
                                from, to
                            )
                        } ?: insnNode.name

                        insnNode.owner = mapClassName(newOwner, from, to) ?: newOwner
                        insnNode.desc = mapMethodDesc(insnNode.desc, from, to)
                    }

                    is MultiANewArrayInsnNode -> {
                        insnNode.desc = mapType(insnNode.desc, from, to)
                    }

                    is TypeInsnNode -> {
                        insnNode.desc = mapClassName(insnNode.desc, from, to) ?: insnNode.desc
                    }
                }
            }
        }

        return insn
    }
}