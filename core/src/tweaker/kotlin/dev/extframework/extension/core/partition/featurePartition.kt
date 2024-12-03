package dev.extframework.extension.core.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.asResourceStream
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.Archives
import dev.extframework.archives.extension.InsnList
import dev.extframework.archives.extension.Method
import dev.extframework.archives.extension.overloads
import dev.extframework.archives.transform.Sources
import dev.extframework.boot.archive.ArchiveData
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.extension.core.THIS_DESCRIPTOR
import dev.extframework.extension.core.feature.FeatureReference
import dev.extframework.extension.core.feature.FeatureType
import dev.extframework.extension.core.feature.IllegalFeatureException
import dev.extframework.extension.core.util.currentCFVersion
import dev.extframework.extension.core.util.withDots
import dev.extframework.extension.core.util.withSlashes
import dev.extframework.tooling.api.extension.ExtensionClassLoader
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.descriptor
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.partitionNamed
import kotlinx.coroutines.runBlocking
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Modifier
import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

public class FeaturePartitionMetadata : ExtensionPartitionMetadata {
    override val name: String = FeaturePartitionLoader.TYPE
}

public data class FeaturePartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
) : ExtensionPartition

public class FeatureIntrinsics {
    private var inited = false
    private lateinit var partitions: Map<String, ExtensionPartitionContainer<*, *>>

    public fun __invoke__(
        classloader: ClassLoader,
        partitionName: String,
        qualifiedSignature: String,
        vararg vars: Any
    ): Any? {
        if (!inited) {
            inited = true
            partitions = (classloader.parent as? ExtensionClassLoader)?.partitions?.associate {
                it.metadata.name to it
            }
                ?: throw RuntimeException("Illegal environment! Features parent classloader should be the extension loader!")
        }

        val callingPartition = partitions[partitionName]
            ?: throw RuntimeException("Illegal environment! Failed to find partition: '$partitionName' while executing feature: '$qualifiedSignature'.")
        check(callingPartition.metadata is TargetPartitionMetadata) { "Illegal request for feature implementation. Features currently only support target partitions." }

        val (featureRef, implPartitionName) = (callingPartition.metadata as TargetPartitionMetadata).implementedFeatures
            .find { it.first.qualifiedSignature == qualifiedSignature }
            ?: throw RuntimeException("Failed to find signature for feature: '$qualifiedSignature' when executing this feature.")

        val implementingPartition = partitions[implPartitionName]
            ?: throw RuntimeException("Illegal environment! Failed to find partition: '$implPartitionName' while executing feature: '$qualifiedSignature'.")

        val containerClass = implementingPartition.node.archive?.classloader?.loadClass(featureRef.container.withDots())
            ?: throw RuntimeException("Implementing partition ($implPartitionName) does not have an archive.")

        val featureSigMethod = Method(featureRef.reference)
        val method = containerClass.declaredMethods.find {
            featureSigMethod.overloads(Method.getMethod(it))
        }
            ?: throw RuntimeException("Failed to find method matching : '$featureSigMethod' in feature container: '$containerClass'")

        val container = if (method.modifiers.and(Modifier.STATIC) == Modifier.STATIC) {
            null
        } else try {
            containerClass.getConstructor().newInstance()
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Failed to find a no-arg constructor in feature container: '$containerClass'.")
        }

        return method.invoke(
            container,
            *vars
        )
    }
}

public data class FeatureCodeSource(
    public val intrinsics: FeatureIntrinsics
) : CodeSource(
    null,
    arrayOf<Certificate>()
)

public class FeaturePartitionLoader(
    private val partitionResolver: PartitionResolver
) : ExtensionPartitionLoader<FeaturePartitionMetadata> {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "feature-holder"
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper,
    ): Job<FeaturePartitionMetadata> {
        return SuccessfulJob { FeaturePartitionMetadata() }
    }

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        check(artifact.metadata.extension.erm.partitions.none {
            it.name == type
        }) { "Erm: '${artifact.metadata.extension.erm.name}' contains a reserved partition name: '$type'" }

        // TODO
        //    The reason we do this is to allow feature partitions to access
        //    the FeatureCodeSource and FeatureIntrinsics. This is not super
        //    elegant and should be figured out in future releases. Especially
        //    THe use of `ExtensionRepositorySettings.local()` is clunky.
        val coreTree = helper.cache(
            PartitionArtifactRequest(THIS_DESCRIPTOR),
            ExtensionRepositorySettings.local(), // Doesn't matter, won't ever get checked,
            partitionResolver
        )().merge()

        helper.newData(
            artifact.metadata.descriptor,
            listOf(coreTree),
        )
    }

    override fun load(
        metadata: FeaturePartitionMetadata,
        reference: ArchiveReference?,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, FeaturePartitionMetadata>> = job {
        runBlocking {
            val mainPartition = (helper.erm.partitions.find { it.type == MainPartitionLoader.TYPE }
                ?: noMainPartition(helper.erm, helper))

            val definedFeatures: Map<String, List<FeatureReference>> =
                (helper.metadataFor(
                    mainPartition
                )().merge() as MainPartitionMetadata
                        ).definedFeatures.groupBy { it.container }

            val targetPartitions = helper.erm.partitions
                .filter { it.type == TargetPartitionLoader.TYPE }
                .map { helper.metadataFor(it)().merge() }
                .filterIsInstance<TargetPartitionMetadata>()
                .filter { it.enabled }

            val implementedFeatures =
                targetPartitions
                    .flatMap { metadata -> metadata.implementedFeatures.map { it to metadata } }
                    .groupBy { it.first.first.container }

            ExtensionPartitionContainer(
                helper.erm.descriptor.partitionNamed(metadata.name),
                metadata,
                run {
                    val extLoaderDefinedBuiltIn = "${FeatureBuiltIn::class.java.name.withSlashes()}.class"

                    val featureBuiltInName = "FeatureBuiltIn-GeneratedFor-${helper.erm.name}"

                    // We know that in a dynamically created partition there will always be an archive to start from.
                    // See @DefaultPartitionResolver
                    reference!!.writer.put(
                        ArchiveReference.Entry(
                            "$featureBuiltInName.class",
                            Resource(
                                (FeatureBuiltIn::class.java.classLoader.getResource(extLoaderDefinedBuiltIn)
                                    ?: cantFindBuiltin()).toURI()
                            ) {
                                buildFeatureBuiltIn(featureBuiltInName)
                            },
                            false,
                            reference
                        )
                    )

                    definedFeatures.forEach { (container, theseDefinedFeatures) ->
                        val implementingContainer =
                            implementedFeatures[container]
                                ?: throw IllegalFeatureException("Feature container: '$container' is not implemented in any partitions.") {
                                    targetPartitions.map { it.name } asContext "Enabled target partitions"
                                    if (targetPartitions.isEmpty()) solution("You dont have any partitions enabled, make sure that you support the current target version.")
                                    else solution("Use @ImplementFeatures to define a feature implementing container.")
                                }

                        theseDefinedFeatures.forEach { def ->
                            val implementations =
                                implementingContainer.filter { it.first.first.name == def.name && it.first.first.container == def.container }
                            if (implementations.isEmpty())
                                throw IllegalFeatureException(
                                    "Found feature: '$def' that is not implemented by any currently active partition."
                                ) {
                                    targetPartitions.map { it.name } asContext "Enabled target partitions"
                                }

                            if (implementations.size > 1) {
                                throw IllegalFeatureException(
                                    "Found feature: '$def' that is implemented by multiple active partitions."
                                ) {
                                    implementations.map { it.second.name } asContext "Implementing target partitions"
                                }
                            }
                        }

                        val containerLocation = container.withSlashes() + ".class"

                        val synthesizedFeatureClass = synthesizeFeatureClass(
                            container.withSlashes(),
                            implementingContainer.map { (ref, part) ->
                                part.name to ref.first
                            },
                            featureBuiltInName
                        )

                        reference.writer.put(
                            ArchiveReference.Entry(
                                containerLocation,
                                DelegatingResource("<synthesized feature class>") {
                                    val writer = ClassWriter(Archives.WRITER_FLAGS)
                                    synthesizedFeatureClass.accept(writer)

                                    ByteArrayInputStream(writer.toByteArray()).asResourceStream()
                                },
                                false,
                                reference
                            )
                        )
                    }

                    FeaturePartitionNode(
                        PartitionArchiveHandle(
                            metadata.name,
                            PartitionClassLoader(
                                helper.erm.descriptor.partitionNamed(metadata.name),
                                accessTree,
                                reference,
                                helper.parentClassLoader,
                                sourceDefiner = { name, byteBuffer, classLoader, definer ->
                                    definer(
                                        name,
                                        byteBuffer,
                                        ProtectionDomain(
                                            FeatureCodeSource(
                                                FeatureIntrinsics()
                                            ), null, classLoader, null
                                        )
                                    )
                                }
                            ),
                            reference,
                            setOf()
                        ),
                        accessTree,
                    )
                })
        }
    }

    private fun synthesizeFeatureClass(
        featureClsName: String,
        implementedFeatures: List<Pair<String, FeatureReference>>,
        featureBuiltInName: String
    ): ClassNode {
        val cls = ClassNode()
        cls.visit(
            currentCFVersion(),
            Opcodes.ACC_PUBLIC,
            featureClsName, null, "java/lang/Object",
            arrayOf()
        )

        implementedFeatures
            .onEach { (p, f) ->
                if (implementedFeatures.any {
                        it.first != p && f.qualifiedSignature == it.second.qualifiedSignature
                    }) throw IllegalFeatureException(
                    "Duplicate/clashing features found: '$f' in separate partitions."
                )
            }.map { (partitionName, ref) ->
                when (ref.type) {
                    FeatureType.CLASS -> TODO()
                    FeatureType.METHOD -> {
                        val method = Method(ref.reference)
                        val methodNode = MethodNode(
                            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                            method.name,
                            method.descriptor,
                            null,
                            arrayOf()
                        )

                        methodNode.instructions = stubCall(
                            partitionName,
                            ref.qualifiedSignature,
                            // Below is the somewhat strange math to get to 1 if the method is static (and thus
                            // needs
                            //               AND -> 0 or Modifier.STATIC         -> 0 or -1    -> 1 or 0
                            argCount = (methodNode.access.and(Modifier.STATIC) / -Modifier.STATIC) + 1 + method.argumentTypes.size,
                            method.returnType,
                            featureBuiltInName
                        )

                        cls.methods.add(methodNode)
                    }

                    FeatureType.FIELD -> TODO()
                }
            }

        return cls
    }

    private fun cantFindBuiltin(): Nothing {
        throw RuntimeException("Error loading FeatureBuiltIn. This classloader was: '${this::class.java.classLoader}'")
    }

    private fun buildFeatureBuiltIn(
        correctedName: String
    ): InputStream {
        val reader =
            ClassReader(
                FeatureBuiltIn::class.java.classLoader.getResourceAsStream(
                    FeatureBuiltIn::class.java.name.withSlashes() + ".class"
                )
                    ?: cantFindBuiltin()
            )
        val node = ClassNode()
        reader.accept(node, 0)

        node.name = correctedName

        val writer = ClassWriter(Archives.WRITER_FLAGS)
        node.accept(writer)
        return ByteArrayInputStream(writer.toByteArray())
    }

    private fun stubCall(
        partition: String,
        qualifiedSignature: String,
        argCount: Int, // includes `this` ref,
        returnType: Type,
        featureBuiltInName: String
    ): InsnList {
        val baseSource =
            Sources.of(StubFeatureBuiltInCall::__stub__)
                .get()
        incrementLocals(baseSource, argCount)

        // Insert feature specific metadata
        baseSource
            .filterIsInstance<LdcInsnNode>()
            .filter { it.cst == StubFeatureBuiltInCall.PARTITION_NAME_QUALIFIER }
            .forEach { it.cst = partition }

        baseSource
            .filterIsInstance<LdcInsnNode>()
            .filter { it.cst == StubFeatureBuiltInCall.FEATURE_SIGNATURE_QUALIFIER }
            .forEach { it.cst = qualifiedSignature }

        baseSource
            .filterIsInstance<LdcInsnNode>()
            .filter { it.cst == Type.getType(FeatureBuiltIn::class.java) }
            .forEach { it.cst = Type.getType("L$featureBuiltInName;") }

        // Find the start of the array creation used to call intrinsics
        val varsStart = baseSource.first {
            it is InsnNode && it.opcode == Opcodes.ICONST_0 &&
                    it.next is TypeInsnNode && it.next.opcode == Opcodes.ANEWARRAY
        }
        val arraySizeInsn = pushI(argCount)
        baseSource.insert(
            varsStart,
            arraySizeInsn
        )
        baseSource.remove(varsStart)

        var lastInsn = arraySizeInsn.next

        for (i in 0 until argCount) {
            val localList = InsnList()
            localList.add(
                InsnNode(Opcodes.DUP)
            )
            localList.add(
                pushI(i)
            )
            localList.add(
                VarInsnNode(Opcodes.ALOAD, i)
            )
            val lastInsnOfLocal = InsnNode(Opcodes.AASTORE)
            localList.add(
                lastInsnOfLocal
            )

            baseSource.insert(
                lastInsn,
                localList
            )
            lastInsn = lastInsnOfLocal
        }

        val returnNode = baseSource.find {
            it.opcode == Opcodes.ARETURN
        }

        fun cast(to: Type): TypeInsnNode {
            return TypeInsnNode(Opcodes.CHECKCAST, to.internalName)
        }

        when (returnType.sort) {
            Type.VOID -> {
                baseSource.insert(returnNode, InsnNode(Opcodes.RETURN))
                baseSource.remove(returnNode)
            }

            Type.OBJECT, Type.ARRAY -> {
                baseSource.insertBefore(returnNode, cast(returnType))
            }

            Type.BOOLEAN -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Boolean;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.IRETURN))
                baseSource.remove(returnNode)
            }

            Type.CHAR -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Character;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.IRETURN))
                baseSource.remove(returnNode)
            }

            Type.BYTE -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Byte;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.IRETURN))
                baseSource.remove(returnNode)
            }

            Type.SHORT -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Short;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.IRETURN))
                baseSource.remove(returnNode)
            }

            Type.INT -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Integer;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.IRETURN))
                baseSource.remove(returnNode)
            }
            Type.FLOAT -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Float;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.FRETURN))
                baseSource.remove(returnNode)
            }
            Type.LONG -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Long;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()L")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.LRETURN))
                baseSource.remove(returnNode)
            }
            Type.DOUBLE -> {
                val list = InsnList(
                    listOf(
                        cast(Type.getType("Ljava/lang/Double;")),
                        MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D")
                    )
                )

                baseSource.insertBefore(returnNode, list)

                baseSource.insert(returnNode, InsnNode(Opcodes.DRETURN))
                baseSource.remove(returnNode)
            }
        }

        return baseSource
    }

    private fun pushI(
        int: Int
    ): AbstractInsnNode = when (int) {
        0 -> InsnNode(Opcodes.ICONST_0)
        1 -> InsnNode(Opcodes.ICONST_1)
        2 -> InsnNode(Opcodes.ICONST_2)
        3 -> InsnNode(Opcodes.ICONST_3)
        4 -> InsnNode(Opcodes.ICONST_4)
        5 -> InsnNode(Opcodes.ICONST_5)
        else -> VarInsnNode(Opcodes.BIPUSH, int)
    }

    private fun incrementLocals(
        list: InsnList,
        increment: Int
    ) {
        list.forEach {
            when (it) {
                is VarInsnNode -> {
                    it.`var` += increment
                }

                is IincInsnNode -> {
                    it.`var` += increment
                }
            }
        }
    }
}