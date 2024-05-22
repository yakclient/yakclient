package net.yakclient.components.extloader.extension.partition

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.asResourceStream
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.extension.Method
import net.yakclient.archives.extension.methodOf
import net.yakclient.archives.extension.overloads
import net.yakclient.archives.transform.ByteCodeUtils
import net.yakclient.archives.transform.Sources
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.components.extloader.ExtensionLoader
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.partition.*
import net.yakclient.components.extloader.extension.ExtensionClassLoader
import net.yakclient.components.extloader.extension.feature.FeatureReference
import net.yakclient.components.extloader.extension.feature.FeatureType
import net.yakclient.components.extloader.extension.feature.IllegalFeatureException
import net.yakclient.components.extloader.util.parseNode
import net.yakclient.components.extloader.util.withDots
import net.yakclient.components.extloader.util.withSlashes
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.lang.RuntimeException
import java.lang.reflect.Modifier
import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

public class FeaturePartitionMetadata(

) : ExtensionPartitionMetadata {
    override val name: String = FeaturePartitionLoader.TYPE
}

public data class FeaturePartitionNode(
    override val archive: ArchiveHandle,
    override val access: ArchiveAccessTree,
) : ExtensionPartitionNode

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

        val partition = partitions[partitionName]
            ?: throw RuntimeException("Illegal environment! Failed to find partition: '$partitionName' while executing feature: '$qualifiedSignature'.")
        check(partition.metadata is VersionedPartitionMetadata) { "Illegal request for feature implementation. Features currently only support version partitions." }

        val featureRef = (partition.metadata as VersionedPartitionMetadata).implementedFeatures
            .find { it.qualifiedSignature == qualifiedSignature }
            ?: throw RuntimeException("Failed to find signature for feature: '$qualifiedSignature' when executing this feature.")

        val containerClass = partition.node.archive.classloader.loadClass(featureRef.container.withDots())

        val featureSigMethod = Method(featureRef.signature)
        val method = containerClass.declaredMethods.find {
            featureSigMethod.overloads(Method.getMethod(it))
        } ?: throw RuntimeException("Failed to find method matching : '$featureSigMethod' in feature container: '$containerClass'")

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
    File(System.getProperty("java.io.tmpdir")).toURI().toURL(),
    arrayOf<Certificate>()
)

public class FeaturePartitionLoader : ExtensionPartitionLoader<FeaturePartitionMetadata> {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "feature-holder"
    }

    override fun parseMetadata(
        partition: ExtensionPartition,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper,
    ): Job<FeaturePartitionMetadata> {
        return SuccessfulJob { FeaturePartitionMetadata() }
    }

    override fun load(
        metadata: FeaturePartitionMetadata,
        reference: ArchiveReference,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<FeaturePartitionNode, FeaturePartitionMetadata>> = job {
        check(helper.runtimeModel.partitions.none {
            it.name == type
        }) { "Erm: '${helper.runtimeModel.name}' contains a reserved partition name: '$type'" }

        val otherPartitions = helper.partitions.values

        val mainPartition: MainPartitionMetadata = otherPartitions
            .filterIsInstance<MainPartitionMetadata>().firstOrNull()
            ?: noMainPartition(metadata, helper)

        val definedFeatures: Map<String, List<FeatureReference>> =
            mainPartition.definedFeatures.groupBy { it.container }

        val targetPartitions: List<VersionedPartitionMetadata> = otherPartitions
            .filterIsInstance<VersionedPartitionMetadata>()
            .filter(VersionedPartitionMetadata::enabled)

        val implementedFeatures: Map<String, List<Pair<FeatureReference, VersionedPartitionMetadata>>> =
            targetPartitions
                .flatMap { p -> p.implementedFeatures.map { it to p } }
                .groupBy {
                    it.first.container
                }

        ExtensionPartitionContainer<FeaturePartitionNode, FeaturePartitionMetadata>(
            helper.thisDescriptor,
            metadata
        ) { linker ->
            val extLoaderDefinedBuiltIn = "${FeatureBuiltIn::class.java.name.withSlashes()}.class"
            val featureBuiltInName = "FeatureBuiltIn-GeneratedFor-${helper.runtimeModel.name}"
            reference.writer.put(
                ArchiveReference.Entry(
                    "$featureBuiltInName.class",
                    Resource(
                        (ExtensionLoader::class.java.classLoader.getResource(extLoaderDefinedBuiltIn)
                            ?: cantFindBuiltin()).toURI()
                    ) {
                        buildFeatureBuiltIn(featureBuiltInName)
                    },
                    false,
                    reference
                )
            )

            definedFeatures.forEach { (container, theseDefinedFeatures) ->
                val implementations: List<Pair<FeatureReference, VersionedPartitionMetadata>> =
                    implementedFeatures[container]
                        ?: throw IllegalFeatureException("Feature: '$container' is not implemented in any enabled partitions.") {
                            targetPartitions.map { it.name } asContext "Enabled version partitions"
                        }

                theseDefinedFeatures.forEach { def ->
                    if (!implementations.any { it.first.name == def.name && it.first.container == def.container })
                        throw IllegalFeatureException(
                            "Found feature: '$def' that is not implemented by any currently active partition."
                        ) {
                            targetPartitions.map { it.name } asContext "Enabled version partitions"
                        }
                }

                val containerLocation = container.replace('.', '/') + ".class"
                val base = mainPartition.archive.getResource(containerLocation)
                    ?.parseNode()
                    ?: throw IllegalFeatureException("Failed to find base feature definition for container: '$container' in main partition.") {

                    }

                synthesizeFeatureClass(
                    base, implementations.map { (ref, part) ->
                        part.name to ref
                    },
                    featureBuiltInName
                )

                reference.writer.put(
                    ArchiveReference.Entry(
                        containerLocation,
                        DelegatingResource("<synthesized feature class>") {
                            val writer = ClassWriter(Archives.WRITER_FLAGS)
                            base.accept(writer)

                            ByteArrayInputStream(writer.toByteArray()).asResourceStream()
                        },
                        false,
                        reference
                    )
                )
            }

            val access = helper.access {
                withDefaults()
                rawTarget(linker.targetTarget)
            }
            FeaturePartitionNode(
                PartitionArchiveHandle(
                    metadata.name,
                    helper.runtimeModel,
                    PartitionClassLoader(
                        helper.runtimeModel,
                        metadata.name,
                        access,
                        reference,
                        helper.parentClassloader,
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
                access,
            )
        }
    }

    private fun synthesizeFeatureClass(
        base: ClassNode,
        implementedFeatures: List<Pair<String, FeatureReference>>,
        featureBuiltInName: String
    ) {
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
                        val method = Method(ref.signature)
                        val methodNode = base.methodOf(method)
                            ?: throw IllegalFeatureException("Feature: '$ref's signature cannot be found in class: '${base.name}'.") {}

                        methodNode.instructions = stubCall(
                            partitionName,
                            ref.qualifiedSignature,
                            // Below is the somewhat strange math to get to 1 if the method is static (and thus
                            // needs
                            //               AND -> 0 or Modifier.STATIC         -> 0 or -1    -> 1 or 0
                            argCount = (methodNode.access.and(Modifier.STATIC) / -Modifier.STATIC) + 1 + method.argumentTypes.size,
                            method.returnType == Type.VOID_TYPE,
                            featureBuiltInName
                        )
                    }

                    FeatureType.FIELD -> TODO()
                }
            }
    }

    private fun cantFindBuiltin(): Nothing {
        throw RuntimeException("Error loading FeatureBuiltIn. This classloader was: '${this::class.java.classLoader}'")
    }

    private fun buildFeatureBuiltIn(
        correctedName: String
    ): InputStream {
        val reader =
            ClassReader(ExtensionLoader::class.java.classLoader.getResourceAsStream(FeatureBuiltIn::class.java.name.withSlashes() + ".class") ?: cantFindBuiltin())
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
        argCount: Int, // includes this ref,
        returnsVoid: Boolean,
        featureBuiltInName: String
    ): InsnList {
        val baseSource = Sources.of(StubFeatureBuiltInCall::___stub___).get()
        incrementLocals(baseSource, argCount)

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


        if (returnsVoid) {
            val returnNode = baseSource.find {
                ByteCodeUtils.isReturn(it.opcode)
            }

            baseSource.insert(returnNode, InsnNode(Opcodes.RETURN))
            baseSource.remove(returnNode)
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