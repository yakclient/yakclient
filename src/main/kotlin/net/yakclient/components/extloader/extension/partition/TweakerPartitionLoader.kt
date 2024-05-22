package net.yakclient.components.extloader.extension.partition

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.job
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.client.api.Extension
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.partition.*
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionContainer
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker
import java.nio.file.Path


public class TweakerPartitionLoader : ExtensionPartitionLoader<TweakerPartitionMetadata> {
    override val type: String = TYPE


    public companion object {
        public const val TYPE: String = "tweaker"
    }

    override fun parseMetadata(
        partition: ExtensionPartition,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper
    ): Job<TweakerPartitionMetadata> = job {
        val tweakerCls = partition.options["tweaker-class"]
            ?: throw IllegalArgumentException("Tweaker partition from extension: '${partition.name}' must contain a tweaker class defined as option: 'tweaker-class'.")

        TweakerPartitionMetadata(tweakerCls)
    }

    override fun load(
        metadata: TweakerPartitionMetadata,
        reference: ArchiveReference,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*,*>> = job {
        val access = helper.access {
            withDefaults()
        }

        val cl = PartitionClassLoader(
            helper.runtimeModel,
            "${helper.runtimeModel.name}-tweaker",
            access,
            reference,
            helper.parentClassloader
        )

        val handle = PartitionArchiveHandle(
            "${helper.runtimeModel.name}-tweaker",
            helper.runtimeModel,
            cl,
            reference,
            setOf()
//            helper.parents.mapNotNullTo(HashSet()) {
//                it.archive
//            }
        )


        val extensionClass = runCatching(ClassNotFoundException::class) {
            handle.classloader.loadClass(
                metadata.tweakerClass
            )
        } ?: throw IllegalArgumentException("Could not load tweaker partition: '${metadata.name}' because the class: '${metadata.tweakerClass}' couldnt be found.")

        val extensionConstructor =
            runCatching(NoSuchMethodException::class) { extensionClass.getConstructor() }
                ?: throw IllegalArgumentException("Could not find no-arg constructor in class: '${metadata.tweakerClass}' in partition: '${metadata.name}'.")

        val instance = extensionConstructor.newInstance() as? EnvironmentTweaker
            ?: throw IllegalArgumentException("Tweaker class: '${metadata.tweakerClass}' does not implement: '${EnvironmentTweaker::class.qualifiedName} in extension: '${metadata.name}'.")

        val node = TweakerPartitionNode(
            handle,
            access,
            instance
        )

        ExtensionPartitionContainer(helper.thisDescriptor,metadata, node )
    }
}

public data class TweakerPartitionMetadata(
    val tweakerClass: String
): ExtensionPartitionMetadata {
    override val name: String = TweakerPartitionLoader.TYPE
}

public data  class TweakerPartitionNode(
    override val archive: ArchiveHandle,
    override val access: ArchiveAccessTree,
    val tweaker: EnvironmentTweaker,
) : ExtensionPartitionNode