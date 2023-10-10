package net.yakclient.components.extloader.tweaker.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.open
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenMetadataHandler
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.components.extloader.api.tweaker.TweakerRuntimeModel

internal class TweakerMetadataHandler(
    settings: SimpleMavenRepositorySettings
) : SimpleMavenMetadataHandler(settings) {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun requestMetadata(
        desc: SimpleMavenDescriptor
    ): Either<MetadataRequestException, SimpleMavenArtifactMetadata> = either.eager {
        val metadata = super.requestMetadata(desc).bind()
        val trm = layout.resourceOf(desc.group, desc.artifact, desc.version, "trm", "json").bind()

        TweakerArtifactMetadata(
            metadata.descriptor,
            metadata.resource,
            metadata.children,
//            mapper.readValue<TweakerRuntimeModel>(trm.open())
        )
    }
}