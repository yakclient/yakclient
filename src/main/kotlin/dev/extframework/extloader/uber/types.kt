package dev.extframework.extloader.uber

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactMetadata.Descriptor
import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.archive.ArchiveNodeResolver
import kotlin.random.Random

public data class UberDescriptor(
    override val name: String,
) : Descriptor {
    // TODO this is not a good solution
    val randomId: String = randomId()

    override fun toString(): String {
        return name
    }

    internal companion object {
        // The odds of this colliding are infinitesimally small, but still not a good solution.
        // Also, if it does, the average user will just restart and then it will fix itself.
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun randomId(): String = buildString {
            repeat(8) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
    }
}

public data class UberParentRequest<D : Descriptor, T : ArtifactRequest<D>, S : RepositorySettings>(
    val request: T,
    val repository: S,
    val resolver: ArchiveNodeResolver<D, T, *, S, *>
)

public data class UberArtifactRequest(
    override val descriptor: UberDescriptor,
    val parents: List<UberParentRequest<*, *, *>>
) : ArtifactRequest<UberDescriptor> {
    public constructor(name: String, parents: List<UberParentRequest<*, *, *>>) : this(UberDescriptor(name), parents)
}

public data class UberNode(
    override val access: ArchiveAccessTree,
    override val descriptor: UberDescriptor
) : ArchiveNode<UberDescriptor>

public object UberRepositorySettings : RepositorySettings

public class UberArtifactMetadata(
    descriptor: UberDescriptor,
    public val requestedParents: List<UberParentRequest<*, *, *>>
) : ArtifactMetadata<UberDescriptor, Nothing>(descriptor, listOf())

public object UberArtifactRepository :
    ArtifactRepository<UberRepositorySettings, UberArtifactRequest, UberArtifactMetadata> {
    override val factory: UberRepositoryFactory = UberRepositoryFactory
    override val name: String = "uber"
    override val settings: UberRepositorySettings = UberRepositorySettings

    override fun get(request: UberArtifactRequest): AsyncJob<UberArtifactMetadata> = asyncJob {
        UberArtifactMetadata(request.descriptor, request.parents)
    }
}

public object UberRepositoryFactory : RepositoryFactory<UberRepositorySettings, UberArtifactRepository> {
    override fun createNew(settings: UberRepositorySettings): UberArtifactRepository {
        return UberArtifactRepository
    }
}
