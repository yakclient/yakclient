package net.yakclient.plugins.yakclient.extension.yak
//
//import com.durganmcbroom.artifact.resolver.ArtifactGraphProvider
//import com.durganmcbroom.artifact.resolver.ArtifactResolutionOptions
//import com.durganmcbroom.artifact.resolver.RepositorySettings
//import com.durganmcbroom.artifact.resolver.group.ResolutionGroupConfig
//
//public interface YakExtManifestDependencyManager {
//    public fun getProvider(repo: YakErmRepository) : ArtifactGraphProvider<*, *>
//
//    public fun registerWith(config: ResolutionGroupConfig, repo: YakErmRepository)
//
//    public fun getRepositorySettings(repo: YakErmRepository) : RepositorySettings
//
//    public fun getArtifactResolutionOptions(repo: YakErmRepository, dependency: YakErmDependency) : ArtifactResolutionOptions
//}