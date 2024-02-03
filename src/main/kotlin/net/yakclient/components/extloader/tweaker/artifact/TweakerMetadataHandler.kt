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
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.components.extloader.api.tweaker.TweakerRuntimeModel
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactMetadata
import net.yakclient.components.extloader.extension.artifact.ExtensionMetadataHandler

internal class TweakerMetadataHandler(
    settings: SimpleMavenRepositorySettings, providers: DependencyTypeContainer
) : ExtensionMetadataHandler(settings, providers) {
    override fun requestMetadata(desc: SimpleMavenDescriptor): Either<MetadataRequestException, ExtensionArtifactMetadata> = either.eager {
        ensure(desc.classifier == "tweaker") { MetadataRequestException.MetadataNotFound }

        super.requestMetadata(desc.copy(classifier = null)).bind()
    }
}