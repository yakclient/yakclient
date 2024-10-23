package dev.extframework.extension.core.mixin

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import dev.extframework.archives.extension.Method
import dev.extframework.archives.extension.isPrimitive
import dev.extframework.archives.extension.methodOf
import dev.extframework.archives.extension.toMethod
import dev.extframework.archives.mixin.*
import dev.extframework.archives.transform.InstructionResolver
import dev.extframework.archives.transform.ProvidedInstructionReader
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.core.api.mixin.InjectionContinuation
import dev.extframework.core.api.mixin.InjectionDefaults.SELF_REF
import dev.extframework.core.api.mixin.MethodInjection
import dev.extframework.core.api.mixin.SourceInjection
import dev.extframework.core.api.mixin.FieldInjection
import dev.extframework.core.api.mixin.InjectionDefaults.SELF_REF_ACCESS
import dev.extframework.extension.core.util.withDots
import dev.extframework.extension.core.util.withSlashes
import dev.extframework.`object`.ObjectContainer
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*

public class SourceInjectionProvider(
    private val sourceInjections: ObjectContainer<SourceInjectionPoint>
) : MixinInjectionProvider<SourceInjection, SourceInjectionProvider.RichSourceInjectionData> {
    override val type: String = "source"
    override val annotationType: Class<SourceInjection> =
        SourceInjection::class.java

    override fun get(): MixinInjection<RichSourceInjectionData> =
        object : MixinInjection<RichSourceInjectionData> {
            override fun apply(
                data: RichSourceInjectionData
            ): TransformerConfig.Mutable = TransformerConfig.of {
                val source = AlterThisReference(
                    data.instructionResolver,
                    data.classTo.withSlashes(),
                    data.classSelf.withSlashes()
                )

                val continuationResultType = Type.getType(InjectionContinuation.Result::class.java)
                val continuationType = Type.getType(InjectionContinuation::class.java)

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
                        insn.add(LdcInsnNode("Error encountered in post mixin injection result handling. Expected a result with ordinance of 0-11, found something else."))
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
                                "dev/extframework/core/api/mixin/InjectionContinuation\$Early${type.internalName}Return"
                            else "dev/extframework/core/api/mixin/InjectionContinuation\$EarlyObjReturn"

                        val returnOpcode = data.methodTo.returnType.getOpcode(Opcodes.IRETURN)

                        if (returnOpcode == Opcodes.RETURN) {
                            insn.add(InsnNode(returnOpcode))
                        } else {
                            val injectedReturnType =
                                getEarlyReturnWrapperForType(data.methodTo.returnType)
                            insn.add(TypeInsnNode(Opcodes.CHECKCAST, injectedReturnType))

                            if (data.methodTo.returnType.isPrimitive) {
                                insn.add(
                                    MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        injectedReturnType,
                                        "value",
                                        "()${data.methodTo.returnType.descriptor}"
                                    )
                                )
                            } else {
                                insn.add(
                                    MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        injectedReturnType,
                                        "value",
                                        "()Ljava/lang/Object;"
                                    )
                                )

                                insn.add(
                                    TypeInsnNode(
                                        Opcodes.CHECKCAST,
                                        data.methodTo.returnType.internalName
                                    )
                                )
                            }
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
        context: MixinInjectionProvider.InjectionContext<SourceInjection>,
    ): Job<RichSourceInjectionData> {
        val methodNode = context.element.target.methodNode
        val classNode = context.element.target.classNode

        return job(
            JobName("Parse source injection class: '${classNode.name}' for source injected method: '${methodNode.toMethod()}'")
        ) {
            val self = classNode.name.withDots()
            val point = context.element.annotation.point

            val targetClass = context.targetNode.name
            val targetMethod = Method(context.element.annotation.methodTo.takeUnless { it == SELF_REF }
            // TODO using the desc as done below is incorrect because the desc will contain the InjectionContinuation.
                ?: (methodNode.name + methodNode.desc))

            val targetMethodNode = context.targetNode.methodOf(targetMethod)
                ?: throw MixinException(message = "Failed to find method: '$targetMethod' in target class: '${context.targetNode.name}'") {
                    targetClass asContext "Target class name"

                    targetMethod.descriptor asContext "Target method signature"
                }

            val originStatic = methodNode.access.and(Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC
            val descStatic = targetMethodNode.access.and(Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC

            if (originStatic.xor(descStatic))
                throw MixinException(null, "Method access's dont match! One is static and the other is not.") {
                    targetClass asContext "Target class name"

                    targetMethod.descriptor asContext "Target method signature"

                    self asContext "Mixin classname"
                    (methodNode.name + methodNode.desc) asContext "@SourceInjection method descriptor"
                }

            val data = RichSourceInjectionData(
                context.extension.artifact,
                targetClass,
                self.withSlashes(),
                ProvidedInstructionReader(
                    methodNode.instructions
                ),
                methodNode.toMethod(),
                targetMethod,
                checkNotNull(sourceInjections.get(point)) {
                    "Illegal injection point: '$point', current registered options are: '${sourceInjections.objects().keys}'"
                },
                originStatic
            )

            data
        }
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

public class MethodInjectionProvider :
    MixinInjectionProvider<MethodInjection, MethodInjectionData> {
    override val type: String = "method"
    override val annotationType: Class<MethodInjection> =
        MethodInjection::class.java

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<MethodInjection>,
    ): Job<MethodInjectionData> {
        val methodNode = context.element.target.methodNode
        val classNode = context.element.target.classNode

        return job(
            JobName(
                "Parse whole method injection from mixin class: '${classNode.name}' " +
                        "as method: '${methodNode.name}'"
            )
        ) {
            val self = classNode.name.withDots()

            val to = context.targetNode.name
            val annotation: MethodInjection = context.element.annotation

            MethodInjectionData(
                to,
                self,
                run {
                    ProvidedInstructionReader(
                        methodNode.instructions
                    )
                },
                annotation.access.takeUnless { it == SELF_REF_ACCESS } ?: methodNode.access,
                annotation.name.takeUnless { it == SELF_REF } ?: methodNode.name,
                annotation.descriptor.takeUnless { it == SELF_REF } ?: methodNode.desc,
                annotation.signature.takeUnless { it == SELF_REF } ?: methodNode.signature,
                annotation.exceptions.takeUnless { it == SELF_REF }?.split(",") ?: methodNode.exceptions
            )
        }
    }

    override fun get(): MixinInjection<MethodInjectionData> = MethodInjection
}

public class FieldInjectionProvider :
    MixinInjectionProvider<FieldInjection, FieldInjectionData> {
    override val type: String = "field"
    override val annotationType: Class<FieldInjection> = FieldInjection::class.java

    override fun parseData(
        context: MixinInjectionProvider.InjectionContext<FieldInjection>,
    ): Job<FieldInjectionData> {
        val target = context.element.target

        return job(
            JobName(
                "Parse whole field injection from mixin class: '${target.classNode.name}' " +
                        "as field: '${target.fieldNode.name}'"
            )
        ) {
            val annotation = context.element.annotation
            val fieldNode = target.fieldNode
            FieldInjectionData(
                annotation.access.takeUnless { it == SELF_REF_ACCESS } ?: fieldNode.access,
                annotation.name.takeUnless { it == SELF_REF } ?: fieldNode.name,
                annotation.type.takeUnless { it == SELF_REF } ?: fieldNode.desc,
                annotation.signature.takeUnless { it == SELF_REF } ?: fieldNode.signature,
            )
        }
    }

    override fun get(): MixinInjection<FieldInjectionData> = FieldInjection
}