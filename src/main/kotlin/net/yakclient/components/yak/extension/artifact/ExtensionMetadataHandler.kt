package net.yakclient.components.yak.extension.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.open
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository.Companion.toPomRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.dependency.DependencyProviders
import net.yakclient.components.yak.extension.ExtensionMetadata
import net.yakclient.components.yak.extension.ExtensionMixin
import net.yakclient.components.yak.extension.ExtensionRuntimeModel


public class ExtensionMetadataHandler(settings: SimpleMavenRepositorySettings, private val providers: DependencyProviders) : SimpleMavenMetadataHandler(settings) {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun requestMetadata(desc: SimpleMavenDescriptor): Either<MetadataRequestException, ExtensionArtifactMetadata> {
        val simpleMaven = providers["simple-maven"]
            ?: throw IllegalStateException("SimpleMaven not found in dependency providers!")

        return either.eager {
            val (group, artifact, version) = desc

            val ermOr = layout.resourceOf(group, artifact, version, "erm", "json").bind()
            val erm = mapper.readValue<ExtensionRuntimeModel>(ermOr.open())

            val mixinsOr = layout.resourceOf(group, artifact, version, "mixins", "json").bind()
            val mixins = mapper.readValue<List<ExtensionMixin>>(mixinsOr.open())

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
                                (simpleMaven.parseSettings(settings) as? SimpleMavenRepositorySettings)?.toPomRepository()
                                    ?: throw IllegalArgumentException("Unknown repository declaration: '$settings' in extension runtime model: '$desc' at '${ermOr.location}'. Cannot parse.")
                            )
                        },
                        "compile"
                    )
                },
                ExtensionMetadata(
                    erm, mixins
                )
            )
        }
    }

    public class ExtensionRequestParsingException internal constructor(
        request: Map<String, String>,
        descriptor: ExtensionDescriptor,
    ) : MetadataRequestException("Failed to parse artifact request for dependency: '$request'. Found in extension '$descriptor'")
}