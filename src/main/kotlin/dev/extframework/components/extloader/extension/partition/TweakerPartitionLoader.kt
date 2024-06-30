package dev.extframework.components.extloader.extension.partition

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.common.util.runCatching
import dev.extframework.components.extloader.api.extension.ExtensionPartition
import dev.extframework.components.extloader.api.extension.partition.*
import dev.extframework.components.extloader.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.components.extloader.api.tweaker.EnvironmentTweaker


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