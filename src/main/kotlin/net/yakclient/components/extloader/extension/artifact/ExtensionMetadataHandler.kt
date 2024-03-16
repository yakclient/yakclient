package net.yakclient.components.extloader.extension.artifact

import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.ResourceRetrievalException
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository.Companion.toPomRepository
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepositoryPolicy
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.openStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel


public open class ExtensionMetadataHandler(
    settings: SimpleMavenRepositorySettings,
    private val providers: DependencyTypeContainer
) : SimpleMavenMetadataHandler(settings) {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun requestMetadata(desc: SimpleMavenDescriptor): Job<ExtensionArtifactMetadata> =
        job(JobName("Load extension metadata for: '$desc'")) {
            val simpleMaven = providers.get("simple-maven")
                ?: throw IllegalStateException("SimpleMaven not found in dependency providers!")

            val (group, artifact, version) = desc

            val ermOr = layout.resourceOf(group, artifact, version, "erm", "json")().merge()
            val erm = mapper.readValue<ExtensionRuntimeModel>(ermOr.openStream())

            val children = erm.extensions

            ExtensionArtifactMetadata(
                desc,
                layout.resourceOf(
                    group,
                    artifact,
                    version,
                    null,
                    erm.packagingType
                )().getOrNull(),
                children.map { req1 ->
                    val req = simpleMaven.parseRequest(req1) as? SimpleMavenArtifactRequest
                        ?: throw ExtensionRequestParsingException(req1, desc)

                    SimpleMavenChildInfo(
                        req.descriptor,
                        erm.extensionRepositories.map { settings ->
                            SimpleMavenRepositoryStub(
                                (simpleMaven.parseSettings(settings) as? SimpleMavenRepositorySettings)?.toPomOrLocalRepository()
                                    ?: throw ResourceRetrievalException.IllegalState("Unknown repository declaration: '$settings' in extension runtime model: '$desc' at '${ermOr.location}'. Cannot parse."),
                                this@ExtensionMetadataHandler.settings.requireResourceVerification,
                            )
                        },
                        "compile"
                    )
                },
                erm
            )
        }

    private fun SimpleMavenRepositorySettings.toPomOrLocalRepository(): PomRepository? {
        return toPomRepository() ?: run {
            val layout = layout as? SimpleMavenLocalLayout ?: return@run null

            return PomRepository(
                null,
                layout.name,
                mavenLocal,
                "ext-local",
                PomRepositoryPolicy(
                    true
                ),
                PomRepositoryPolicy(
                    true
                )
            )
        }
    }

    public class ExtensionRequestParsingException internal constructor(
        request: Map<String, String>,
        descriptor: ExtensionDescriptor,
    ) : MetadataRequestException("Failed to parse artifact request for dependency: '$request'. Found in extension '$descriptor'")
}