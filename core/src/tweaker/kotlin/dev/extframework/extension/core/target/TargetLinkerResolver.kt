package dev.extframework.extension.core.target

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey
import java.nio.file.Path
import java.nio.file.Paths

public class TargetNode(
    override val handle: ArchiveHandle,
) : ClassLoadedArchiveNode<TargetDescriptor> {
    override val descriptor: TargetDescriptor = TargetDescriptor
    override val access: ArchiveAccessTree = object : ArchiveAccessTree {
        override val descriptor: ArtifactMetadata.Descriptor = this@TargetNode.descriptor
        override val targets: List<ArchiveTarget> = listOf()
    }
}

public object TargetArtifactMetadata : ArtifactMetadata<TargetDescriptor, Nothing>(
    TargetDescriptor,
    listOf()
)

public class TargetLinkerResolver(
    private val linker: TargetLinker,
) : ArchiveNodeResolver<
        TargetDescriptor,
        TargetArtifactRequest,
        TargetNode,
        TargetRepositorySettings,
        TargetArtifactMetadata
        >, EnvironmentAttribute {
    override val metadataType: Class<TargetArtifactMetadata> = TargetArtifactMetadata::class.java
    override val name: String = "target"
    override val nodeType: Class<in TargetNode> = TargetNode::class.java

    override val key: EnvironmentAttributeKey<*> = TargetLinkerResolver

    public companion object : EnvironmentAttributeKey<TargetLinkerResolver>

    override fun deserializeDescriptor(descriptor: Map<String, String>, trace: ArchiveTrace): Result<TargetDescriptor> =
        result { TargetDescriptor }

    override fun serializeDescriptor(descriptor: TargetDescriptor): Map<String, String> {
        return mapOf()
    }

    override fun pathForDescriptor(descriptor: TargetDescriptor, classifier: String, type: String): Path {
        return Paths.get("target", "target-$classifier.$type")
    }

    override fun load(
        data: ArchiveData<TargetDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<TargetNode> = job {
        TargetNode(
            classLoaderToArchive(linker.targetLoader)
        )
    }

    override fun createContext(settings: TargetRepositorySettings): ResolutionContext<TargetRepositorySettings, TargetArtifactRequest, TargetArtifactMetadata> {
        return ResolutionContext(
            object : ArtifactRepository<TargetRepositorySettings, TargetArtifactRequest, TargetArtifactMetadata> {
                override val factory: RepositoryFactory<TargetRepositorySettings, ArtifactRepository<TargetRepositorySettings, TargetArtifactRequest, TargetArtifactMetadata>>
                    get() = throw UnsupportedOperationException()
                override val name: String = "target"
                override val settings: TargetRepositorySettings = settings

                override fun get(request: TargetArtifactRequest): Job<TargetArtifactMetadata> {
                    return SuccessfulJob {
                        TargetArtifactMetadata
                    } as Job<Nothing>
                }
            }
        )
    }

    override fun cache(
        artifact: Artifact<TargetArtifactMetadata>,
        helper: CacheHelper<TargetDescriptor>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.newData(TargetDescriptor, listOf())
    }
}