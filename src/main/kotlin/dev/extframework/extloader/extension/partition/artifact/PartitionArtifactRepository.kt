package dev.extframework.extloader.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.resources.ResourceNotFoundException
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor

public open class PartitionArtifactRepository(
    final override val settings: ExtensionRepositorySettings,
    private val prmProvider: (PartitionDescriptor, ExtensionRepositorySettings) -> PartitionRuntimeModel?,
    override val factory: PartitionRepositoryFactory,
) : ArtifactRepository<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata> {
    override val name: String = "partitions@${settings.layout.name}"
    private val layout by settings::layout

    override fun get(
        request: PartitionArtifactRequest
    ): AsyncJob<PartitionArtifactMetadata> = asyncJob(JobName("Load extension metadata for: '${request.descriptor}'")) {
        val (extensionDescriptor, partition) = request.descriptor
        val (group, artifact, version) = extensionDescriptor

        val prm = prmProvider(request.descriptor, settings)

        if (prm == null) {
            throw MetadataRequestException.MetadataNotFound(request.descriptor, "prm/erm.json")
        }

        val resource = try {
            layout.resourceOf(
                group,
                artifact,
                version,
                partition,
                "jar",
            )
        } catch (e: ResourceNotFoundException) {
            null
        } catch (e: Throwable) {
            throw e
        }

        PartitionArtifactMetadata(
            request.descriptor,
            resource,
            prm
        )
    }
}