package net.yakclient.components.extloader.mixin

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.mixin.*
import net.yakclient.archives.transform.InstructionAdapter
import net.yakclient.archives.transform.InstructionResolver
import net.yakclient.archives.transform.MethodSignature
import net.yakclient.archives.transform.ProvidedInstructionReader
import net.yakclient.client.api.annotation.FieldInjection
import net.yakclient.client.api.annotation.InjectionDefaults.SELF_REF
import net.yakclient.client.api.annotation.InjectionDefaults.SELF_REF_ACCESS
import net.yakclient.common.util.equalsAny
import net.yakclient.components.extloader.util.withDots
import net.yakclient.components.extloader.util.withSlashes
import net.yakclient.components.extloader.api.environment.injectionPointsAttrKey
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import net.yakclient.client.api.annotation.MethodInjection
import net.yakclient.client.api.annotation.SourceInjection


private fun mappingInsnAdapterFor(
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

public class SourceInjectionProvider : MixinInjectionProvider<SourceInjection, SourceInjectionData> {
    override val type: String = "source"
    override val annotationType: Class<SourceInjection> = SourceInjection::class.java

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<SourceInjection>,
        mappingContext: MixinInjectionProvider.MappingContext,
        ref: ExtensionArchiveReference
    ): SourceInjectionData {
        val self = context.classNode.name.replace('/', '.')
        val point = context.element.annotation.point

        val clsSelf = ref.reader["${self.withSlashes()}.class"]
            ?: throw IllegalArgumentException("Failed to find class: '$self' in extension when loading source injection.")

        val node = ClassNode().also { ClassReader(clsSelf.resource.open()).accept(it, 0) }

        val toWithSlashes = context.target.name
        val methodTo = context.element.annotation.methodTo.takeUnless { it == SELF_REF } ?: (context.element.methodNode.name + context.element.methodNode.desc)
        val data = SourceInjectionData(
            mappingContext.mappings.mapClassName(toWithSlashes, mappingContext.fromNS, mappingContext.toNS)?.withDots()
                ?: toWithSlashes,
            self.withSlashes(),
            run {
                val methodFrom = context.element.methodNode.name + context.element.methodNode.desc
                mappingInsnAdapterFor(
                    mappingContext.tree,
                    mappingContext.mappings,
                    self.withSlashes(),
                    toWithSlashes,
                    ProvidedInstructionReader(
                        node.methods.firstOrNull {
                            (it.name + it.desc) == methodFrom // Method signature does not get mapped
                        }?.instructions
                            ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                    )
                ).also { it.get() }
            },
            run {
                val signature = MethodSignature.of(methodTo)

                val fullDesc = "(${signature.desc})${signature.returnType}"
                val name = mappingContext.mappings.mapMethodName(
                    toWithSlashes,
                    signature.name,
                    fullDesc,
                    mappingContext.fromNS,
                    mappingContext.toNS
                ) ?: signature.name
                val desc = mappingContext.mappings.mapMethodDesc(
                    fullDesc, mappingContext.fromNS,
                    mappingContext.toNS
                )
                name + desc
            },
            checkNotNull(mappingContext.environment[injectionPointsAttrKey]?.container?.get(point)) {
                "Illegal injection point: '$point', current registered options are: '${mappingContext.environment[injectionPointsAttrKey]?.container?.objects()?.keys ?: listOf()}'"
            }
        )

        return data
    }

    override fun get(): MixinInjection<SourceInjectionData> = SourceInjection
}

public class MethodInjectionProvider : MixinInjectionProvider<MethodInjection, MethodInjectionData> {
    override val type: String = "method"
    override val annotationType: Class<MethodInjection> = MethodInjection::class.java

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<MethodInjection>,
        mappingContext: MixinInjectionProvider.MappingContext,
        ref: ExtensionArchiveReference
    ): MethodInjectionData {
        val self = context.classNode.name.replace('/', '.')
        val classSelf = ref.reader["${self.withSlashes()}.class"] ?: throw IllegalArgumentException(
            "Failed to find class: '$self' when loading method injections."
        )
        val node = ClassNode().also { ClassReader(classSelf.resource.open()).accept(it, 0) }

        val to = context.target.name
        val annotation: MethodInjection = context.element.annotation

        val methodSelf = context.element.methodNode

        return MethodInjectionData(
            mappingContext.mappings.mapClassName(
                to, mappingContext.fromNS,
                mappingContext.toNS
            )?.withDots() ?: to,
            self,
            run {
                val methodFrom = methodSelf.name + methodSelf.desc
                mappingInsnAdapterFor(
                    mappingContext.tree,
                    mappingContext.mappings,
                    self.withSlashes(),
                    to,
                    ProvidedInstructionReader(
                        node.methods.firstOrNull {
                            (it.name + it.desc) == methodFrom // Method signature does not get mapped
                        }?.instructions
                            ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                    )
                )
            },

            annotation.access.takeUnless { it == SELF_REF_ACCESS } ?: methodSelf.access,
            annotation.name.takeUnless { it == SELF_REF } ?: methodSelf.name,
            annotation.descriptor.takeUnless {it == SELF_REF} ?: methodSelf.desc,
            annotation.signature.takeUnless { it == SELF_REF } ?: methodSelf.signature,
            annotation.exceptions.takeUnless { it == SELF_REF }?.split(",") ?: methodSelf.exceptions
        )
    }

    override fun get(): MixinInjection<MethodInjectionData> = MethodInjection
}

public class FieldInjectionProvider : MixinInjectionProvider<FieldInjection, FieldInjectionData> {
    override val type: String = "field"
    override val annotationType: Class<FieldInjection> = FieldInjection::class.java

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<FieldInjection>,
        mappingContext: MixinInjectionProvider.MappingContext,
        ref: ExtensionArchiveReference
    ): FieldInjectionData {
        val annotation = context.element.annotation
        val fieldNode = context.element.fieldNode
        return FieldInjectionData(
            annotation.access.takeUnless { it == SELF_REF_ACCESS } ?: fieldNode.access,
            annotation.name.takeUnless { it == SELF_REF } ?: fieldNode.name,
            annotation.type.takeUnless { it == SELF_REF } ?: fieldNode.desc,
            annotation.signature.takeUnless { it == SELF_REF } ?: fieldNode.signature,
        )
    }

    override fun get(): MixinInjection<FieldInjectionData> = FieldInjection
}