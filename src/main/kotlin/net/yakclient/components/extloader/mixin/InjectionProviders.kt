package net.yakclient.components.extloader.mixin

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.mixin.*
import net.yakclient.archives.transform.InstructionAdapter
import net.yakclient.archives.transform.InstructionResolver
import net.yakclient.archives.transform.MethodSignature
import net.yakclient.archives.transform.ProvidedInstructionReader
import net.yakclient.components.extloader.ExtensionLoader
import net.yakclient.components.extloader.util.withDots
import net.yakclient.components.extloader.util.withSlashes
import net.yakclient.internal.api.InternalRegistry
import net.yakclient.internal.api.extension.archive.ExtensionArchiveReference
import net.yakclient.internal.api.mixin.MixinInjectionProvider
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.logging.Level


private fun Map<String, String>.notNull(name: String): String =
        checkNotNull(this[name]) { "Invalid Mixin options, no '$name' value provided in '$this'." }

private fun ArchiveMapping.justMapSignatureDescriptor(signature: String): String {
    val (name, desc, ret) = MethodSignature.of(signature)

    val retOrBlank = ret ?: ""
    return name + mapMethodDesc("($desc)$retOrBlank", MappingDirection.TO_FAKE)
}

internal fun registerBasicProviders() {
    listOf(
            SourceInjectionProvider(),
            MethodInjectionProvider(),
            FieldInjectionProvider(),
    ).forEach { InternalRegistry.mixinTypeContainer.register(it.type, it) }
}

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
            val mappedName = mappings.mapClassName(name, MappingDirection.TO_REAL) ?: name
            return transform(mappedName)
                    ?: (interfaces + (listOfNotNull(superClass))).firstNotNullOfOrNull { it.fromTreeInternal(transform) }
        }

        fun <R> fromTree(start: String, transform: (String) -> R?): R? {
            return tree[mappings.mapClassName(start, MappingDirection.TO_FAKE)]?.fromTreeInternal(transform)
        }

        val direction = MappingDirection.TO_FAKE
        mappings.run {
            insn.forEach { insnNode ->
                when (insnNode) {
                    is FieldInsnNode -> {
                        val newOwner = if (insnNode.owner == from && insnNode.opcode != Opcodes.GETSTATIC) to else insnNode.owner

                        insnNode.name = fromTree(newOwner) {
                            mapFieldName(
                                    it,
                                    insnNode.name,
                                    direction
                            )
                        } ?: insnNode.name

                        insnNode.owner = mapClassName(newOwner, direction) ?: newOwner
                        insnNode.desc = mapType(insnNode.desc, direction)
                    }

                    is InvokeDynamicInsnNode -> {
                        val newOwner = if (insnNode.bsm.owner == from) to else insnNode.bsm.owner

                        fun Handle.mapHandle(): Handle = Handle(
                                tag,
                                mapClassName(newOwner, direction) ?: newOwner,
                                fromTree(newOwner) {
                                    mapMethodName(
                                            it,
                                            name,
                                            desc,
                                            direction
                                    )
                                } ?: name,
                                mapMethodDesc(desc, direction),
                                isInterface
                        )

                        // Type and Handle
                        insnNode.bsm = insnNode.bsm.mapHandle()


                        // Can ignore name because only the name of the bootstrap method is known at compile time and that is held in the handle field
                        insnNode.desc =
                                mapMethodDesc(
                                        insnNode.desc,
                                        direction
                                ) // Expected descriptor type of the generated call site
                    }

                    is MethodInsnNode -> {
                        val newOwner = if (insnNode.owner == from && insnNode.opcode != Opcodes.INVOKESTATIC) to else insnNode.owner

                        insnNode.name = fromTree(newOwner) {
                            mapMethodName(
                                    it,
                                    insnNode.name,
                                    insnNode.desc,
                                    direction
                            )
                        } ?: insnNode.name

                        insnNode.owner = mapClassName(newOwner, direction) ?: newOwner
                        insnNode.desc = mapMethodDesc(insnNode.desc, direction)
                    }

                    is MultiANewArrayInsnNode -> {
                        insnNode.desc = mapType(insnNode.desc, direction)
                    }

                    is TypeInsnNode -> {
                        insnNode.desc = mapClassName(insnNode.desc, direction) ?: insnNode.desc
                    }
                }
            }
        }

        return insn
    }
}


public class SourceInjectionProvider : MixinInjectionProvider<SourceInjectionData> {
    override val type: String = "source"
    private val pointCache = HashMap<String, SourceInjectionPoint>()

    override fun parseData(
            options: Map<String, String>,
            mappingContext: MixinInjectionProvider.MappingContext,
            ref: ExtensionArchiveReference
    ): SourceInjectionData {
        val self = options.notNull("self")
        val point = options.notNull("point")

        val clsSelf = ref.reader["${self.withSlashes()}.class"]
                ?: throw IllegalArgumentException("Failed to find class: '$self' in extension when loading source injection.")

        val node = ClassNode().also { ClassReader(clsSelf.resource.open()).accept(it, 0) }

        val toWithSlashes = options.notNull("to").withSlashes()
        val methodTo = options.notNull("methodTo")
        val data = SourceInjectionData(
                mappingContext.mappings.mapClassName(toWithSlashes, MappingDirection.TO_FAKE)?.withDots()
                        ?: toWithSlashes,
                self,
                run {
                    val methodFrom = options.notNull("methodFrom")
                    mappingInsnAdapterFor(
                            mappingContext.tree,
                            mappingContext.mappings,
                            self.withSlashes(),
                            toWithSlashes,
                            ProvidedInstructionReader(
                                    node.methods.firstOrNull {
                                        (it.name + it.desc) == mappingContext.mappings.justMapSignatureDescriptor(methodFrom)
                                    }?.instructions
                                            ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                            )
                    )
                },
                run {
                    val signature = MethodSignature.of(methodTo)

                    val fullDesc = "(${signature.desc})${signature.returnType}"
                    val name = mappingContext.mappings.mapMethodName(
                            toWithSlashes,
                            signature.name,
                            fullDesc,
                            MappingDirection.TO_FAKE
                    ) ?: signature.name
                    val desc = mappingContext.mappings.mapMethodDesc(fullDesc, MappingDirection.TO_FAKE)
                    name + desc
                },
                checkNotNull(InternalRegistry.injectionPointContainer.get(point)) { "Illegal injection point: '$point', current registered options are: '${InternalRegistry.injectionPointContainer.objects().keys}'" }.also { pointCache[point] = it }
        )

        return data
    }

    override fun get(): MixinInjection<SourceInjectionData> = SourceInjection
}

public class MethodInjectionProvider : MixinInjectionProvider<MethodInjectionData> {
    override val type: String = "method"

    override fun parseData(
            options: Map<String, String>,
            mappingContext: MixinInjectionProvider.MappingContext,
            ref: ExtensionArchiveReference
    ): MethodInjectionData {
        val self = options.notNull("self")
        val classSelf = ref.reader["${self.withSlashes()}.class"] ?: throw IllegalArgumentException(
                "Failed to find class: '$self' when loading method injections."
        )
        val node = ClassNode().also { ClassReader(classSelf.resource.open()).accept(it, 0) }

        val to = options.notNull("to").withSlashes()
        return MethodInjectionData(
                mappingContext.mappings.mapClassName(to, MappingDirection.TO_FAKE)?.withDots() ?: to,
                self,
                run {
                    val methodFrom = options.notNull("methodFrom")
                    mappingInsnAdapterFor(
                            mappingContext.tree,
                            mappingContext.mappings,
                            self.withSlashes(),
                            to,
                            ProvidedInstructionReader(
                                    node.methods.firstOrNull {
                                        (it.name + it.desc) == mappingContext.mappings.justMapSignatureDescriptor(methodFrom)
                                    }?.instructions
                                            ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                            )
                    )
                },

                options.notNull("access").toInt(),
                options.notNull("name"),
                options.notNull("description"),
                options["signature"],
                options.notNull("exceptions").split(',')
        )
    }

    override fun get(): MixinInjection<MethodInjectionData> = MethodInjection
}

public class FieldInjectionProvider : MixinInjectionProvider<FieldInjectionData> {
    override val type: String = "field"

    override fun parseData(
            options: Map<String, String>,
            mappingContext: MixinInjectionProvider.MappingContext,
            ref: ExtensionArchiveReference
    ): FieldInjectionData {
        return FieldInjectionData(
                options.notNull("access").toInt(),
                options.notNull("name"),
                options.notNull("type"),
                options["signature"],
                run {
                    if (options["value"] != null) ExtensionLoader.logger.log(
                            Level.WARNING,
                            "Cannot set initial values in mixins at this time, this will eventually be a feature."
                    )
                    null
                }
        )
    }

    override fun get(): MixinInjection<FieldInjectionData> = FieldInjection
}