package net.yakclient.components.extloader.extension.partition

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.openStream
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.client.api.Extension
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.descriptor
import net.yakclient.components.extloader.api.extension.partition.*
import net.yakclient.components.extloader.extension.ExtensionNode
import net.yakclient.components.extloader.extension.feature.FeatureReference
import net.yakclient.components.extloader.extension.feature.containsFeatures
import net.yakclient.components.extloader.extension.feature.findFeatures
import net.yakclient.components.extloader.util.parseNode
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashSet


public class MainPartitionLoader : ExtensionPartitionLoader<MainPartitionMetadata> {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "main"
    }

    override fun parseMetadata(
        partition: ExtensionPartition,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper
    ): Job<MainPartitionMetadata> = job {
        val allFeatures = reference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .map {
                it.resource.openStream().parseNode()
            }.filter {
                it.containsFeatures()
            }.flatMap {
                it.findFeatures()
            }.toList()

        MainPartitionMetadata(
            allFeatures,
            partition.options["extension-class"]
                ?: throw IllegalArgumentException("Main partition from extension: '${helper.runtimeModel.descriptor}' must contain an extension class defined as option: 'extension-class'."),
            reference
        )
    }

    override fun load(
        metadata: MainPartitionMetadata,
        reference: ArchiveReference,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, *>> = job {
        val featurePartitionName = "feature-holder-${UUID.randomUUID()}"

        val featurePartition = ExtensionPartition(
            FeaturePartitionLoader.TYPE,
            featurePartitionName,
            "/META-INF/partitions/$featurePartitionName",
            helper.runtimeModel.partitions.flatMap { it.repositories },
            helper.runtimeModel.partitions.flatMap { it.dependencies },
            mapOf()
        )
        helper.addPartition(
            featurePartition
        )

        ExtensionPartitionContainer<MainPartitionNode, MainPartitionMetadata>(
            helper.thisDescriptor,
            metadata,
        ) { linker -> // The reason we use a target enabled partition for this one is because main depends on the feature partition which is target enabled. It doesnt make sense for a non target enabled partition to rely on an enabled one.
            val access = helper.access {
                withDefaults()

                helper.partitions.entries.find {
                    it.value is TweakerPartitionMetadata
                }?.let {
                    direct(helper.load(it.key))
                }

                direct(helper.load(featurePartition).also {
                    (it as TargetRequiringPartitionContainer<*, *>).setup(linker)().merge()
                })

                helper.parents.flatMap { it ->
                    it.partitions
                        .filter { it.metadata is MainPartitionMetadata }
                }.forEach {
                    direct(it)
                }
            }

            val sourceProviderDelegate = ArchiveSourceProvider(reference)

            val cl = PartitionClassLoader(
                helper.runtimeModel,
                "${helper.runtimeModel.name}-main",
                access,
                reference,
                helper.parentClassloader,
                sourceProvider = object : SourceProvider by sourceProviderDelegate {
                    private val featureContainers = metadata.definedFeatures.mapTo(HashSet()) { it.container }

                    override fun findSource(name: String): ByteBuffer? {
                        return if (featureContainers.contains(name)) null
                        else sourceProviderDelegate.findSource(name)
                    }
                }
            )

            val handle = PartitionArchiveHandle(
                "${helper.runtimeModel.name}-main",
                helper.runtimeModel,
                cl,
                reference,
                setOf()
            )

            val extName = helper.runtimeModel.name

            val extClass = metadata.extensionClass

            val extensionClass =
                runCatching(ClassNotFoundException::class) { handle.classloader.loadClass(extClass) }
                    ?: throw IllegalArgumentException("Could not load extension: '$extName' because the class: '${extClass}' couldnt be found.")

            val extensionConstructor =
                runCatching(NoSuchMethodException::class) { extensionClass.getConstructor() }
                    ?: throw IllegalArgumentException("Could not find no-arg constructor in class: '${extClass}' in extension: '$extName'.")

            val instance = extensionConstructor.newInstance() as? Extension
                ?: throw IllegalArgumentException("Extension class: '${extClass}' does not implement: '${Extension::class.qualifiedName} in extension: '$extName'.")

//            ExtensionPartitionContainer(

            MainPartitionNode(
                handle,
                access,
                instance,
            )
        }
    }
}

//public abstract class MainPartitionContainer(
//    override val data: ExtensionPartition,
//    ,
//) : ExtensionPartitionContainer<MainPartitionNode>

public data class MainPartitionMetadata(
    val definedFeatures: List<FeatureReference>,
    val extensionClass: String,
    val archive: ArchiveReference,
) : ExtensionPartitionMetadata {
    override val name: String = "main"
}

public data class MainPartitionNode(
    override val archive: ArchiveHandle,
    override val access: ArchiveAccessTree,
    val extension: Extension,
) : ExtensionPartitionNode {
}