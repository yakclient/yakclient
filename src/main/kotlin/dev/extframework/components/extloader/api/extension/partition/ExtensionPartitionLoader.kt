package dev.extframework.components.extloader.api.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ResolutionHelper
import dev.extframework.components.extloader.api.environment.ExtLoaderEnvironment
import dev.extframework.components.extloader.api.extension.ExtensionPartition
import dev.extframework.components.extloader.api.extension.ExtensionRuntimeModel
import dev.extframework.components.extloader.extension.ExtensionNode

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