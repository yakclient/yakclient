package dev.extframework.extension.core.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveData
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.ArchiveClassProvider
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.ClassProvider
import dev.extframework.boot.loader.DelegatingClassProvider
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.runCatching
import dev.extframework.extension.core.feature.FeatureReference
import dev.extframework.extension.core.target.TargetArtifactRequest
import dev.extframework.extension.core.target.TargetDescriptor
import dev.extframework.extension.core.target.TargetLinkerResolver
import dev.extframework.extension.core.target.TargetRepositorySettings
import dev.extframework.extension.core.util.emptyArchiveHandle
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.extension.PartitionRuntimeModel
import dev.extframework.internal.api.extension.descriptor
import dev.extframework.internal.api.extension.partition.*
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.internal.api.extension.partition.artifact.partitionNamed
import kotlinx.coroutines.awaitAll

public interface TargetPartitionMetadata : ContingentPartitionMetadata {
    override val name: String
    public val implementedFeatures: List<Pair<FeatureReference, String>>

    //    public val mixins: Sequence<ClassNode>
//    public val mixins: Sequence<MixinTransaction.Metadata<*>>
    public val archive: ArchiveReference
}

public open class TargetPartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
) : ExtensionPartition {
    internal constructor(descriptor: ArtifactMetadata.Descriptor) : this(
        emptyArchiveHandle(),
        object : PartitionAccessTree {
            override val partitions: List<ExtensionPartitionContainer<*, *>> = listOf()
            override val descriptor: ArtifactMetadata.Descriptor = descriptor
            override val targets: List<ArchiveTarget> = listOf()
        }
    )
}

public abstract class TargetPartitionLoader<T : TargetPartitionMetadata>(
    protected val environment: ExtensionEnvironment
) : ContingentPartitionLoader<T> {
    final override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "target"
    }

    abstract override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper
    ): Job<T>

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val parents = helper.parents.mapValues { (_, erm) ->
            erm.erm.partitions.find { it.type == MainPartitionLoader.TYPE } ?: noMainPartition(erm.erm, helper)
        }.toList().mapAsync { (parent, partition) ->
            helper.cache(
                parent, partition
            )().merge()
        }

        val targetResolver = environment[TargetLinkerResolver].extract()

        val target = helper.cache(
            TargetArtifactRequest,
            TargetRepositorySettings,
            targetResolver
        )().merge()

        helper.newData(artifact.metadata.descriptor, parents.awaitAll() + listOf(target))
    }

    override fun load(
        metadata: T,
        reference: ArchiveReference,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, T>> =
        job {
            val thisDescriptor = helper.erm.descriptor.partitionNamed(metadata.name)

            ExtensionPartitionContainer(
                thisDescriptor,
                metadata,
                run {
                    val sourceProviderDelegate = ArchiveSourceProvider(reference)

                    val (dependencies, target) = accessTree.targets
                        .map { it.relationship.node }
                        .filterIsInstance<ClassLoadedArchiveNode<*>>()
                        .partition { it.descriptor != TargetDescriptor }

                    val cl = PartitionClassLoader(
                        thisDescriptor,
                        accessTree,
                        reference,
                        helper.parentClassLoader,
                        sourceProvider = sourceProviderDelegate,
                        classProvider = object : ClassProvider {
                            val dependencyDelegate = DelegatingClassProvider(
                                dependencies
                                    .mapNotNull { it.handle }
                                    .map(::ArchiveClassProvider)
                            )
                            val targetDelegate = target.first().handle!!.classloader

                            override val packages: Set<String> = dependencyDelegate.packages + "*target*"

                            override fun findClass(name: String): Class<*>? {
                                return runCatching(ClassNotFoundException::class) { targetDelegate.loadClass(name) } ?: dependencyDelegate.findClass(name)
                            }
                        }
                    )

                    val handle = PartitionArchiveHandle(
                        "${helper.erm.name}-${metadata.name}",
                        cl,
                        reference,
                        setOf()
                    )

                    TargetPartitionNode(
                        handle,
                        accessTree,
                    )
                }
            )
        }
}