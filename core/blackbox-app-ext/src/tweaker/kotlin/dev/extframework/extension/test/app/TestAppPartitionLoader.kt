package dev.extframework.extension.test.app

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.openStream
import dev.extframework.archives.ArchiveReference
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.delegate.Delegation
import dev.extframework.extension.core.feature.FeatureReference
import dev.extframework.extension.core.feature.findImplementedFeatures
import dev.extframework.extension.core.feature.implementsFeatures
import dev.extframework.extension.core.partition.TargetPartitionLoader
import dev.extframework.extension.core.partition.TargetPartitionMetadata
import dev.extframework.extension.core.util.parseNode
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.partition.PartitionMetadataHelper

public data class TestAppPartitionMetadata(
    override val implementedFeatures: List<Pair<FeatureReference, String>>,
//    override val mixins: Sequence<MixinTransaction.Metadata<*>>,
    override val archive: ArchiveReference,
    override val name: String
) : TargetPartitionMetadata {
    private val disabledPartitions = (System.getProperty("target.partitions.disabled") ?: "").split(",")

    override val enabled: Boolean = !disabledPartitions.contains(name)
}

public class TestAppPartitionLoader(
    environment: ExtensionEnvironment
) : TargetPartitionLoader<TestAppPartitionMetadata>(
    environment
) {
    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper
    ): Job<TestAppPartitionMetadata> = job {
        val processor = environment[AnnotationProcessor].extract()
        val delegation = environment[Delegation].extract()

        val implementedFeatures = reference!!.reader.entries()
            .filter { it.name.endsWith(".class") }
            .map {
                it.resource.openStream().parseNode()
            }.filter {
                it.implementsFeatures(processor)
            }.flatMap {
                it.findImplementedFeatures(partition.name, processor, delegation)
            }.toList()

//        val mixins: Sequence<MixinTransaction.Metadata<*>> = setupMixinContexts(
//            helper.erm.descriptor,
//            reference,
//            environment,
//        )().merge().map {
//            it.createTransactionMetadata(
//                it.context.targetNode.name.withDots(),
//            )().merge()
//        }

        TestAppPartitionMetadata(
            implementedFeatures,
//            mixins,
            reference,
            partition.name,
        )
    }
}