package dev.extframework.components.extloader.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.ResourceNotFoundException
import com.durganmcbroom.resources.openStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.components.extloader.extension.artifact.ExtensionArtifactRepository
import dev.extframework.internal.api.extension.PartitionRuntimeModel
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest


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
    ): Job<PartitionArtifactMetadata> = job(JobName("Load extension metadata for: '${request.descriptor}'")) {
        val (extensionDescriptor, partition) = request.descriptor
        val (group, artifact, version) = extensionDescriptor

        val prmOr = layout.resourceOf(group, artifact, version, partition, "json")().mapException {
            if (it is ResourceNotFoundException) MetadataRequestException.MetadataNotFound(request.descriptor, "$partition.json", it)
            else MetadataRequestException("Failed to request resource for prm: '${request.descriptor}'", it)
        }.merge()
        val prm = mapper.readValue<PartitionRuntimeModel>(prmOr.openStream())

        PartitionArtifactMetadata(
            request.descriptor,
            layout.resourceOf(
                group,
                artifact,
                version,
                partition,
                "jar",
            )().merge(),
            prm,
            extensionRepository.get(
                ExtensionArtifactRequest(extensionDescriptor)
            )().merge(),
        )
    }
}