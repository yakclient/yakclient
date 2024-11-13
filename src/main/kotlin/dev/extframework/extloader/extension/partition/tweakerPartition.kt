package dev.extframework.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.runCatching
import dev.extframework.extloader.util.toOrNull
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.descriptor
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.partitionNamed
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker
import kotlinx.coroutines.awaitAll

public class TweakerPartitionLoader : ExtensionPartitionLoader<TweakerPartitionMetadata> {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "tweaker"
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper,
    ): Job<TweakerPartitionMetadata> = job {
        val tweakerCls = partition.options["tweaker-class"]
            ?: throw IllegalArgumentException("Tweaker partition from extension: '${partition.name}' must contain a tweaker class defined as option: 'tweaker-class'.")

        TweakerPartitionMetadata(tweakerCls)
    }

    override fun load(
        metadata: TweakerPartitionMetadata,
        reference: ArchiveReference,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, TweakerPartitionMetadata>> = job {
        val thisDescriptor = helper.erm.descriptor.partitionNamed(metadata.name)

        val cl = PartitionClassLoader(
            thisDescriptor,
            accessTree,
            reference,
            helper.parentClassLoader
        )

        val handle = PartitionArchiveHandle(
            thisDescriptor.name,
            cl,
            reference,
            setOf()
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
            accessTree,
            instance
        )

        ExtensionPartitionContainer(thisDescriptor, metadata, node)
    }

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val parents = helper.parents
            .mapNotNull {
                it.key toOrNull it.value.erm.partitions.find { p -> p.type == TYPE }
            }.mapAsync {
                helper.cache(it.first, it.second)().merge()
            }

        helper.newData(
            artifact.metadata.descriptor,
            parents.awaitAll()
        )
    }
}

public data class TweakerPartitionMetadata(
    val tweakerClass: String
) : ExtensionPartitionMetadata {
    override val name: String = TweakerPartitionLoader.TYPE
}

public data class TweakerPartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
    val tweaker: EnvironmentTweaker,
) : ExtensionPartition