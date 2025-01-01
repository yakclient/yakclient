package dev.extframework.extloader.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.ResourceNotFoundException
import com.durganmcbroom.resources.toByteArray
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.extloader.extension.artifact.ExtensionArtifactRepository
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest


public open class PartitionArtifactRepository(
    final override val settings: ExtensionRepositorySettings,
    override val factory: PartitionRepositoryFactory,
    private val extensionRepository: ExtensionArtifactRepository,
) : ArtifactRepository<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata> {
    override val name: String = "partitions@${settings.layout.name}"
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val layout by settings::layout

    override fun get(
        request: PartitionArtifactRequest
    ): AsyncJob<PartitionArtifactMetadata> = asyncJob(JobName("Load extension metadata for: '${request.descriptor}'")) {
        val (extensionDescriptor, partition) = request.descriptor
        val (group, artifact, version) = extensionDescriptor

        val prmOr = try {
            layout.resourceOf(group, artifact, version, partition, "json")
        } catch (e: ResourceNotFoundException) {
            throw MetadataRequestException.MetadataNotFound(request.descriptor, "$partition.json", e)
        } catch (e: Throwable) {
            throw MetadataRequestException("Failed to request resource for prm: '${request.descriptor}'", e)
        }

        val prm = mapper.readValue<PartitionRuntimeModel>(prmOr.open().toByteArray())

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
            prm,
            extensionRepository.get(
                ExtensionArtifactRequest(extensionDescriptor)
            )().merge(),
        )
    }
}