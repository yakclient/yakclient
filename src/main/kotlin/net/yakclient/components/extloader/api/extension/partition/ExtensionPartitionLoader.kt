package net.yakclient.components.extloader.api.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ResolutionHelper
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.extension.ExtensionNode

public interface ExtensionPartitionNode {
    public val archive: ArchiveHandle
    public val access: ArchiveAccessTree
}

public interface PartitionLoaderHelper {
    public val environment: ExtLoaderEnvironment
    public val runtimeModel: ExtensionRuntimeModel
    public val parents: List<ExtensionNode>
    public val parentClassloader: ClassLoader
    public val thisDescriptor: ArtifactMetadata.Descriptor
    public val partitions: Map<ExtensionPartition, ExtensionPartitionMetadata>

    public fun addPartition(partition: ExtensionPartition): ExtensionPartitionMetadata

//    public fun load(partition: ExtensionPartition)

//    public fun loadLazy(partition: ExtensionPartition) : ArchiveTarget

    public fun load(partition: ExtensionPartition): ExtensionPartitionContainer<*, *>

    public fun access(scope: PartitionAccessTreeScope.() -> Unit): ArchiveAccessTree
}

public interface PartitionAccessTreeScope : ResolutionHelper.AccessTreeScope {
    public fun withDefaults()

    public fun direct(node: ExtensionPartitionContainer<*, *>)
}

public interface PartitionMetadataHelper {
    //    public fun sub(reference: ArchiveReference, path: Path) : ArchiveReference
    public val runtimeModel: ExtensionRuntimeModel
    public val environment: ExtLoaderEnvironment

}

public interface ExtensionPartitionMetadata {
    public val name: String
}

public interface ExtensionPartitionLoader<T : ExtensionPartitionMetadata> {
    public val type: String

    public fun parseMetadata(
        partition: ExtensionPartition,
//        model: ExtensionRuntimeModel,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper
    ): Job<T>

    public fun load(
        metadata: T,
        reference: ArchiveReference,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, *>>
}