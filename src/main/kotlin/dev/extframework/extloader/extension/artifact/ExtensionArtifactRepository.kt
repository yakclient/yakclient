package dev.extframework.extloader.extension.artifact

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.ResourceRetrievalException
import com.durganmcbroom.jobs.*
import com.durganmcbroom.resources.ResourceNotFoundException
import com.durganmcbroom.resources.openStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.extloader.exception.ExtLoaderExceptions
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionParentInfo
import dev.extframework.tooling.api.extension.descriptor


public open class ExtensionArtifactRepository(
    final override val settings: SimpleMavenRepositorySettings,
    private val providers: DependencyTypeContainer,
    override val factory: ExtensionRepositoryFactory,
) : ArtifactRepository<SimpleMavenRepositorySettings, ExtensionArtifactRequest, ExtensionArtifactMetadata> {
    override val name: String = "extensions@${settings.layout.name}"
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val layout by settings::layout

    override fun get(
        request: ExtensionArtifactRequest
    ): Job<ExtensionArtifactMetadata> = job(JobName("Load extension metadata for: '${request.descriptor}'")) {
        val simpleMaven = providers.get("simple-maven")
            ?: throw IllegalStateException("SimpleMaven not found in dependency providers!")

        val (group, artifact, version) = request.descriptor

        val ermOr = layout.resourceOf(group, artifact, version, "erm", "json")().mapException {
            if (it is ResourceNotFoundException) MetadataRequestException.MetadataNotFound(request.descriptor, "erm.json", it)
            else MetadataRequestException("Failed to request resource for erm: '${request.descriptor}'", it)
        }.merge()
        val erm = result {
            mapper.readValue<ExtensionRuntimeModel>(ermOr.openStream())
        }.mapException {
            StructuredException(
                ExtLoaderExceptions.InvalidErm,
                message = "Invalid Extension runtime model built for extension: '${request.descriptor}'"
            ) {
                TOOLING_API_VERSION asContext "Current API version:"
            }
        }.merge()
        validateErm(request.descriptor, erm)

        val children = erm.parents

        ExtensionArtifactMetadata(
            request.descriptor,
            children.map { req1 ->
                ExtensionParentInfo(
                    ExtensionArtifactRequest(req1.toDescriptor()),
                    erm.repositories.map { settings ->
                        (simpleMaven.parseSettings(settings) as? SimpleMavenRepositorySettings)
                            ?: throw ResourceRetrievalException.IllegalState("Unknown repository declaration: '$settings' in extension runtime model: '${request.descriptor}' at '${ermOr.location}'. Cannot parse.")
                    },
                )
            },
            erm,
            settings
        )
    }

    private fun validateErm(
        descriptor: ExtensionDescriptor,
        erm: ExtensionRuntimeModel,
    ) {
        if (erm.descriptor != descriptor) {
            throw StructuredException(
                ExtLoaderExceptions.InvalidErm,
                message = "Descriptor mismatch. The group:name:version in the erm must match the path at which this artifact is located."
            ) {
                erm.descriptor asContext "ERM descriptor"
            }
        }
        if (erm.apiVersion > TOOLING_API_VERSION) {
            throw StructuredException(
                ExtLoaderExceptions.InvalidErm,
                message = "Unsupported API version."
            ) {
                erm.apiVersion asContext "Extension API version"
                TOOLING_API_VERSION asContext "Current API version"
            }
        }
    }
}