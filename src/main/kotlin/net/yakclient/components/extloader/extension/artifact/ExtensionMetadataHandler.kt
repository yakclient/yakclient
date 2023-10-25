package net.yakclient.components.extloader.extension.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.open
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository.Companion.toPomRepository
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepositoryPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel


public class ExtensionMetadataHandler(
    settings: SimpleMavenRepositorySettings,
    private val providers: DependencyTypeContainer
) : SimpleMavenMetadataHandler(settings) {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun requestMetadata(desc: SimpleMavenDescriptor): Either<MetadataRequestException, ExtensionArtifactMetadata> {
        val simpleMaven = providers.get("simple-maven")
            ?: throw IllegalStateException("SimpleMaven not found in dependency providers!")

        return either.eager {
            val (group, artifact, version) = desc

            val ermOr = layout.resourceOf(group, artifact, version, "erm", "json").bind()
            val erm = mapper.readValue<ExtensionRuntimeModel>(ermOr.open())

            val children = erm.extensions

            ExtensionArtifactMetadata(
                desc,
                layout.resourceOf(
                    group,
                    artifact,
                    version,
                    null,
                    erm.packagingType
                ).orNull(),
                children.map { req1 ->
                    val req = simpleMaven.parseRequest(req1) as? SimpleMavenArtifactRequest ?: shift(
                        ExtensionRequestParsingException(req1, desc)
                    )

                    SimpleMavenChildInfo(
                        req.descriptor,
                        erm.extensionRepositories.map { settings ->
                            SimpleMavenRepositoryStub(
                                (simpleMaven.parseSettings(settings) as? SimpleMavenRepositorySettings)?.toPomOrLocalRepository()
                                    ?: throw IllegalArgumentException("Unknown repository declaration: '$settings' in extension runtime model: '$desc' at '${ermOr.location}'. Cannot parse.")
                            )
                        },
                        "compile"
                    )
                },
                erm
            )
        }
    }

    private fun SimpleMavenRepositorySettings.toPomOrLocalRepository() : PomRepository? {
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