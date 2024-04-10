package net.yakclient.components.extloader.extension.partition

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.asResourceStream
import com.durganmcbroom.resources.openStream
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.extension.methodOf
import net.yakclient.archives.extension.toMethod
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.partition.*
import net.yakclient.components.extloader.extension.feature.FeatureReference
import net.yakclient.components.extloader.extension.feature.FeatureType
import net.yakclient.components.extloader.extension.feature.IllegalFeatureException
import net.yakclient.components.extloader.util.parseNode
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.ClassNode
import java.io.ByteArrayInputStream

public class FeaturePartitionMetadata(

) : ExtensionPartitionMetadata {
    override val name: String = FeaturePartitionLoader.TYPE
}

public data class FeaturePartitionNode(
    override val archive: ArchiveHandle,
    override val access: ArchiveAccessTree
) : ExtensionPartitionNode {
}

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

        val otherPartitions =
            helper.partitions.values

        val mainPartition: MainPartitionMetadata = otherPartitions
            .filterIsInstance<MainPartitionMetadata>().first()

        val definedFeatures: Map<String, List<FeatureReference>> =
            mainPartition.definedFeatures.groupBy { it.container }

        val targetPartitions: List<VersionedPartitionMetadata> = otherPartitions
            .filterIsInstance<VersionedPartitionMetadata>()
            .filter(VersionedPartitionMetadata::enabled)

        val implementedFeatures: Map<String, List<Pair<FeatureReference, VersionedPartitionMetadata>>> = targetPartitions
            .flatMap { p -> p.implementedFeatures.map { it to p } }
            .groupBy {
                it.first.container
            }

        ExtensionPartitionContainer<FeaturePartitionNode, FeaturePartitionMetadata>(helper.thisDescriptor, metadata) { linker ->
            definedFeatures.forEach { (container, theseDefinedFeatures) ->
                val implementations: List<Pair<FeatureReference, VersionedPartitionMetadata>> =
                    implementedFeatures[container]
                        ?: throw IllegalFeatureException("Feature: '$container' is not implemented in any of the following enabled partitions: '${targetPartitions.map { it.name }}'.")

                theseDefinedFeatures.forEach { def ->
                    check(implementations.any { it.first.name == def.name && it.first.container == def.container }) { "Found feature: '$def' that is not implemented by any currently active partition: '${targetPartitions.map { it.name }}'" }
                }

                val cache = HashMap<String, ClassNode>()
                val refsToNodes = implementations.map { (ref, part) ->
                    cache[ref.container]?.let { ref to it } ?: run {
                        val stream =
                            part.archive.reader[ref.container.replace('.', '/') + ".class"]?.resource?.openStream()
                                ?: throw IllegalFeatureException("Failed to find feature implementation of feature: '$ref'")
                        val node = stream.parseNode()

                        cache[ref.container] = node

                        ref to node
                    }
                }

                val containerLocation = container.replace('.', '/') + ".class"
                val base = mainPartition.archive.getResource(containerLocation)
                    ?.parseNode()
                    ?: throw IllegalFeatureException("Failed to find base feature definition for container: '$container' in main partition.")

                synthesizeFeatureClass(base, refsToNodes)

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
                        helper.runtimeModel, metadata.name, access, reference, helper.parentClassloader
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
        implementedFeatures: List<Pair<FeatureReference, ClassNode>>
    ) {
        implementedFeatures.map { (ref, node) ->
            when (ref.type) {
                FeatureType.CLASS -> TODO()
                FeatureType.METHOD -> {
                    val method = Method.getMethod(ref.signature)
                    val methodNode = node.methodOf(method)
                    base.methods.removeIf { it.toMethod() == method }

                    base.methods.add(methodNode)
                }

                FeatureType.FIELD -> TODO()
            }
        }
    }
}