package net.yakclient.plugins.yakclient.extension.yak.artifact
//
//import com.durganmcbroom.artifact.resolver.RepositoryHandler
//import com.durganmcbroom.artifact.resolver.RepositoryReference
//import com.durganmcbroom.artifact.resolver.open
//import com.durganmcbroom.artifact.resolver.simple.maven.HashType
//import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
//import com.durganmcbroom.artifact.resolver.simple.maven.layout.DefaultSimpleMavenLayout
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.module.kotlin.KotlinModule
//import com.fasterxml.jackson.module.kotlin.readValue
//import net.yakclient.plugins.yakclient.extension.yak.YakErm
//
//private const val ERM_ENDING = "erm"
//
//public class YakExtRepositoryHandler(
//    override val settings: YakExtRepositorySettings
//) : RepositoryHandler<YakExtDescriptor, YakExtArtifactMetadata, YakExtRepositorySettings> {
//    private val layout = DefaultSimpleMavenLayout(settings.url, HashType.SHA1)
//    private val mapper = ObjectMapper().registerModule(KotlinModule())
//
//    override fun descriptorOf(name: String): YakExtDescriptor? = YakExtDescriptor.parseDescriptor(name)
//
//    override fun metaOf(descriptor: YakExtDescriptor): YakExtArtifactMetadata? {
//        val (group, artifact, version) = descriptor
//
//        val ermResource = layout.artifactOf(group, artifact, version, null, ERM_ENDING) ?: return null
//        val erm = mapper.readValue<YakErm>(ermResource.open())
//
//        val deps = erm.dependencies.map { d ->
//            YakExtDependencyInfo(
//                descriptorOf(d.notation)
//                    ?: throw IllegalArgumentException("Failed to resolve dependency notation for dependency: '$d'."),
//                erm.repositories.map { r ->
//                    YakExtDependencyRef(
//                        settings.ermReferencer.reference(r)
//                            ?: throw IllegalArgumentException("Failed to create repository reference for repository: '$r'"),
//                        YakExtArtifactResolutionOptions(d.options.isTransitive, _excludes = d.options.exclude.toMutableSet())
//                    )
//                }
//            )
//        }
//
//        val extRepos = erm.extensionRepositories.map {
//            RepositoryReference(YakExtensionRepo, YakExtRepositorySettings(it.configuration.url))
//        }
//        val extDeps = erm.extensionDependencies.map {
//            YakExtTransitiveInfo(
//                descriptorOf(it)
//                    ?: throw IllegalArgumentException("Failed to resolve dependency notation for dependency: '$it'."),
//                extRepos
//            )
//        }
//
//        val ext = layout.artifactOf(group, artifact, version, null, erm.packagingType)
//
//        return YakExtArtifactMetadata(
//            descriptor,
//            ext,
//            extDeps,
//            deps,
//            erm
//        )
//    }
//
//}