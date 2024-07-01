package dev.extframework.components.extloader.mixin

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import dev.extframework.archive.mapper.transform.mapClassName
import dev.extframework.archive.mapper.transform.mapMethodDesc
import dev.extframework.archive.mapper.transform.mapMethodName
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.extension.*
import dev.extframework.archives.mixin.*
import dev.extframework.archives.transform.InstructionResolver
import dev.extframework.archives.transform.ProvidedInstructionReader
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.client.api.annotation.InjectionDefaults.SELF_REF
import dev.extframework.client.api.annotation.InjectionDefaults.SELF_REF_ACCESS
import dev.extframework.components.extloader.api.environment.getOrNull
import dev.extframework.components.extloader.api.environment.injectionPointsAttrKey
import dev.extframework.components.extloader.api.mixin.MixinInjectionProvider
import dev.extframework.components.extloader.util.withDots
import dev.extframework.components.extloader.util.withSlashes
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*

public class SourceInjectionProvider :
    MixinInjectionProvider<dev.extframework.client.api.annotation.SourceInjection, SourceInjectionProvider.RichSourceInjectionData> {
    override val type: String = "source"
    override val annotationType: Class<dev.extframework.client.api.annotation.SourceInjection> = dev.extframework.client.api.annotation.SourceInjection::class.java

    override fun get(): MixinInjection<RichSourceInjectionData> =
        object : MixinInjection<RichSourceInjectionData> {
            override fun apply(
                data: RichSourceInjectionData
            ): TransformerConfig.Mutable = TransformerConfig.of {
                val source = AlterThisReference(
                    data.instructionResolver,
                    data.classTo.replace('.', '/'),
                    data.classSelf.replace('.', '/')
                )

                val continuationResultType = Type.getType(dev.extframework.client.api.InjectionContinuation.Result::class.java)
                val continuationType = Type.getType(dev.extframework.client.api.InjectionContinuation::class.java)

                check(
                    data.methodFrom.returnType == continuationResultType
                ) { "Mixin return type of method: '${data.methodFrom}' of class: '${data.classSelf}' from ext: '${data.extFrom}' was not 'dev.extframework.client.api.InjectionContinuation\$Result'!" }
                val methodFromParameters = data.methodFrom.argumentTypes
                check(
                    methodFromParameters.last() == continuationType
                ) { "Last parameter of mixin source injection: '${data.methodFrom}' of class: '${data.classSelf}' from ext: '${data.extFrom}' should always be: 'dev.extframework.client.api.InjectionContinuation'" }

                val generatedMethodName = "${data.methodFrom.name}GeneratedForExt-${data.extFrom}"

                transformClass(data.classTo) {
                    val injectedMethod = MethodNode()
                    injectedMethod.name = generatedMethodName
                    injectedMethod.desc = data.methodFrom.descriptor
                    injectedMethod.instructions = source.get()
                    injectedMethod.access =
                        if (data.isStatic) Opcodes.ACC_STATIC.or(Opcodes.ACC_PRIVATE) else Opcodes.ACC_PRIVATE

                    it.methods.add(injectedMethod)

                    it
                }

                transformMethod(data.methodTo) { method ->
                    data.methodAt.apply(SourceInjectionContext(method)).forEach {
                        val insn = InsnList()

                        insn.add(LabelNode(Label()))

                        // Load the 'this' reference or in the
                        if (!data.isStatic) insn.add(
                            VarInsnNode(
                                Opcodes.ALOAD,
                                0
                            )
                        )

                        var current: Int = if (data.isStatic) 0 else 1
                        // Load all parameters
                        (methodFromParameters.toMutableList())
                            // Remove the continuation type which must be last
                            .also { it.removeLast() }
                            .forEach { param ->
                                val opcode = param.getOpcode(Opcodes.ILOAD)

                                insn.add(VarInsnNode(opcode, current)) // First parameter is always either
                                current += param.size
                            }

                        insn.add(TypeInsnNode(Opcodes.NEW, continuationType.internalName))
                        insn.add(InsnNode(Opcodes.DUP))
                        insn.add(MethodInsnNode(Opcodes.INVOKESPECIAL, continuationType.internalName, "<init>", "()V"))

                        insn.add(
                            MethodInsnNode(
                                if (data.isStatic) Opcodes.INVOKESTATIC
                                else Opcodes.INVOKESPECIAL,
                                data.classTo,
                                generatedMethodName,
                                data.methodFrom.descriptor
                            )
                        )

                        insn.add(InsnNode(Opcodes.DUP))
                        insn.add(
                            MethodInsnNode(
                                Opcodes.INVOKEINTERFACE,
                                continuationResultType.internalName,
                                "getOrdinance",
                                "()I"
                            )
                        )

                        val defaultLabel = LabelNode(Label())
                        val earlyReturnLabel = LabelNode(Label())
                        val resumeLabel = LabelNode(Label())

                        // Look up switch
                        insn.add(
                            LookupSwitchInsnNode(
                                defaultLabel,
                                intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
                                arrayOf(
                                    earlyReturnLabel, // 0
                                    earlyReturnLabel, // 1
                                    earlyReturnLabel, // 2
                                    earlyReturnLabel, // 3
                                    earlyReturnLabel, // 4
                                    earlyReturnLabel, // 5
                                    earlyReturnLabel, // 6
                                    earlyReturnLabel, // 7
                                    earlyReturnLabel, // 8
                                    earlyReturnLabel, // 9
                                    earlyReturnLabel, // 10
                                    resumeLabel
                                )
                            )
                        )

                        // Default label
                        insn.add(defaultLabel)
                        insn.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;"))
                        insn.add(LdcInsnNode("Error encountered in post mixin injection result handling. Expected a result with ordinance of either 0 (early return), or 1 (resume), found neither."))
                        insn.add(
                            MethodInsnNode(
                                Opcodes.INVOKEVIRTUAL,
                                "java/io/PrintStream",
                                "println",
                                "(Ljava/lang/String;)V"
                            )
                        )
                        insn.add(JumpInsnNode(Opcodes.GOTO, resumeLabel))

                        // Early return
                        insn.add(earlyReturnLabel)

                        fun getEarlyReturnWrapperForType(type: Type): String =
                            if (type.isPrimitive)
                                "dev/extframework/client/api/InjectionContinuation\$Early${type.internalName}Return"
                            else "dev/extframework/client/api/InjectionContinuation\$EarlyObjReturn"

                        val returnOpcode = data.methodTo.returnType.getOpcode(Opcodes.IRETURN)

                        if (returnOpcode == Opcodes.RETURN) {
                            insn.add(InsnNode(returnOpcode))
                        } else {
                            val injectedReturnType =
                                getEarlyReturnWrapperForType(data.methodTo.returnType)
                            insn.add(TypeInsnNode(Opcodes.CHECKCAST, injectedReturnType))

                            insn.add(
                                MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    injectedReturnType,
                                    "value",
                                    "()${data.methodTo.returnType.descriptor}"
                                )
                            )
                            insn.add(InsnNode(returnOpcode))
                        }

                        // Resume
                        insn.add(resumeLabel)
                        insn.add(InsnNode(Opcodes.POP))

                        (it as MixinAdaptedInjector).inject(insn)
                    }

                    method
                }
            }
        }

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<dev.extframework.client.api.annotation.SourceInjection>,
        mappingContext: MixinInjectionProvider.MappingContext,
        ref: ArchiveReference
    ): Job<RichSourceInjectionData> =
        job(
            JobName(
                "Parse source injection data for extension: '${ref.name}' in " +
                        "class: '${context.classNode.name}' for source injected method: " +
                        "'${context.element.methodNode.toMethod()}'"
            )
        ) {
            val self = context.classNode.name.withDots()
            val point = context.element.annotation.point

            val unmappedTargetClass = context.target.name
            val unmappedMethodTo = Method(context.element.annotation.methodTo.takeUnless { it == SELF_REF }
                ?: (context.element.methodNode.name + context.element.methodNode.desc))

            val targetMethod = run {
                val name = mappingContext.mappings.mapMethodName(
                    unmappedTargetClass,
                    unmappedMethodTo.name,
                    unmappedMethodTo.descriptor,
                    mappingContext.fromNS,
                    mappingContext.toNS
                ) ?: unmappedMethodTo.name
                val desc = mappingContext.mappings.mapMethodDesc(
                    unmappedMethodTo.descriptor, mappingContext.fromNS,
                    mappingContext.toNS
                )
                Method(name, desc)
            }
            val targetMethodNode = context.target.methodOf(targetMethod)
                ?: throw MixinException(message = "Failed to find method: '$targetMethod' in target class: '${context.target.name}'") {
                    ref.name asContext "Extension name"

                    unmappedTargetClass asContext "Unmapped target class name"
                    (mappingContext.mappings
                        .mapClassName(unmappedTargetClass, mappingContext.fromNS, mappingContext.toNS)
                        ?.withDots() ?: unmappedTargetClass) asContext "Mapped target class name"

                    unmappedMethodTo.descriptor asContext "Unmapped target method signature"
                    targetMethod.descriptor asContext "Mapped target method signature"

                    mappingContext.fromNS asContext "Unmapped (source) mapping namespace"
                    mappingContext.toNS asContext "Mapped (target) mapping namespace"
                }
            val targetClassname = mappingContext.mappings
                .mapClassName(unmappedTargetClass, mappingContext.fromNS, mappingContext.toNS)
                ?.withDots() ?: unmappedTargetClass

            val originStatic = context.element.methodNode.access.and(Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC
            val descStatic = targetMethodNode.access.and(Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC

            if (originStatic.xor(descStatic))
                throw MixinException(null, "Method access's dont match! One is static and the other is not.") {
                    unmappedTargetClass asContext "Unmapped target class name"
                    targetClassname asContext "Mapped target class name"

                    unmappedMethodTo.descriptor asContext "Unmapped target method signature"
                    targetMethod.descriptor asContext "Mapped target method signature"

                    self asContext "Mixin classname"
                    (context.element.methodNode.name + context.element.methodNode.desc) asContext "@SourceInjection method descriptor"

                    mappingContext.fromNS asContext "Unmapped (source) mapping namespace"
                    mappingContext.toNS asContext "Mapped (target) mapping namespace"
                }

            val data = RichSourceInjectionData(
                mappingContext.extensionName,
                targetClassname,
                self.withSlashes(),
                run {
                    val methodFrom = context.element.methodNode.name + context.element.methodNode.desc
                    mappingInsnAdapterFor(
                        mappingContext.tree,
                        mappingContext.mappings,
                        self.withSlashes(),
                        unmappedTargetClass,
                        ProvidedInstructionReader(
                            context.classNode.methods.firstOrNull {
                                (it.name + it.desc) == methodFrom // Method signature does not get mapped
                            }?.instructions
                                ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                        )
                    )
                },
                context.element.methodNode.toMethod(),
                run {

                    targetMethod
                },
                checkNotNull(mappingContext.environment[injectionPointsAttrKey].getOrNull()?.container?.get(point)) {
                    "Illegal injection point: '$point', current registered options are: '${mappingContext.environment[injectionPointsAttrKey].getOrNull()?.container?.objects()?.keys ?: listOf()}'"
                },
                originStatic
            )

            data
        }

    public data class RichSourceInjectionData(
        val extFrom: String,

        val classTo: String,
        val classSelf: String,

        public val instructionResolver: InstructionResolver,
        public val methodFrom: Method,
        public val methodTo: Method,
        public val methodAt: SourceInjectionPoint,

        public val isStatic: Boolean
    ) : MixinInjection.InjectionData
}

public class MethodInjectionProvider : MixinInjectionProvider<dev.extframework.client.api.annotation.MethodInjection, MethodInjectionData> {
    override val type: String = "method"
    override val annotationType: Class<dev.extframework.client.api.annotation.MethodInjection> = dev.extframework.client.api.annotation.MethodInjection::class.java

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<dev.extframework.client.api.annotation.MethodInjection>,
        mappingContext: MixinInjectionProvider.MappingContext,
        ref: ArchiveReference
    ): Job<MethodInjectionData> =
        job(
            JobName(
                "Parse whole method injection for extension: " +
                        "'${ref.name}' from mixin class: '${context.classNode.name}' " +
                        "as method: '${context.element.methodNode.name}'"
            )
        ) {
            val self = context.classNode.name.replace('/', '.')

            val to = context.target.name
            val annotation: dev.extframework.client.api.annotation.MethodInjection = context.element.annotation

            val methodSelf = context.element.methodNode

            MethodInjectionData(
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
                            context.classNode.methods.firstOrNull {
                                (it.name + it.desc) == methodFrom // Method signature does not get mapped
                            }?.instructions
                                ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                        )
                    )
                },
                annotation.access.takeUnless { it == SELF_REF_ACCESS } ?: methodSelf.access,
                annotation.name.takeUnless { it == SELF_REF } ?: methodSelf.name,
                annotation.descriptor.takeUnless { it == SELF_REF } ?: methodSelf.desc,
                annotation.signature.takeUnless { it == SELF_REF } ?: methodSelf.signature,
                annotation.exceptions.takeUnless { it == SELF_REF }?.split(",") ?: methodSelf.exceptions
            )
        }

    override fun get(): MixinInjection<MethodInjectionData> = MethodInjection
}

public class FieldInjectionProvider : MixinInjectionProvider<dev.extframework.client.api.annotation.FieldInjection, FieldInjectionData> {
    override val type: String = "field"
    override val annotationType: Class<dev.extframework.client.api.annotation.FieldInjection> = dev.extframework.client.api.annotation.FieldInjection::class.java

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<dev.extframework.client.api.annotation.FieldInjection>,
        mappingContext: MixinInjectionProvider.MappingContext,
        ref: ArchiveReference
    ): Job<FieldInjectionData> =
        job(
            JobName(
                "Parse whole field injection for extension: " +
                        "'${ref.name}' from mixin class: '${context.classNode.name}' " +
                        "as field: '${context.element.fieldNode.name}'"
            )
        ) {
            val annotation = context.element.annotation
            val fieldNode = context.element.fieldNode
            FieldInjectionData(
                annotation.access.takeUnless { it == SELF_REF_ACCESS } ?: fieldNode.access,
                annotation.name.takeUnless { it == SELF_REF } ?: fieldNode.name,
                annotation.type.takeUnless { it == SELF_REF } ?: fieldNode.desc,
                annotation.signature.takeUnless { it == SELF_REF } ?: fieldNode.signature,
            )
        }

    override fun get(): MixinInjection<FieldInjectionData> = FieldInjection
}