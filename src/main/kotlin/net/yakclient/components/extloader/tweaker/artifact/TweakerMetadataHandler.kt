package net.yakclient.components.extloader.tweaker.artifact

import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactMetadata
import net.yakclient.components.extloader.extension.artifact.ExtensionMetadataHandler

//internal class TweakerMetadataHandler(
//    settings: SimpleMavenRepositorySettings, providers: DependencyTypeContainer
//) : ExtensionMetadataHandler(settings, providers) {
//    override fun requestMetadata(desc: SimpleMavenDescriptor): Job<ExtensionArtifactMetadata> = job {
//        if (desc.classifier != "tweaker") { throw MetadataRequestException.MetadataNotFound }
//
//        val metadata = super.requestMetadata(desc.copy(classifier = null))().merge()
//
//        ExtensionArtifactMetadata(
//            metadata.descriptor.copy(classifier = "tweaker"),
//            metadata.resource.takeUnless { metadata.erm.tweakerPartition == null },
//            metadata.children.map {
//                it.copy(
//                    it.descriptor.copy(classifier = "tweaker"),
//                    it.candidates
//                )
//            },
//            metadata.erm
//        )
//    }
//}