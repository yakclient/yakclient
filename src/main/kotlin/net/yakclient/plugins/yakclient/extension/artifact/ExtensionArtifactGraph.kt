package net.yakclient.plugins.yakclient.extension.artifact
//
//public class ExtensionArtifactGraph(
//    config: ExtensionGraphConfig,
//    provider: ArtifactGraphProvider<ExtensionGraphConfig, *>
//) :
//    ArtifactGraph<ExtensionGraphConfig, YakExtRepositorySettings, ExtensionArtifactGraph.ExtensionArtifactResolver>(
//        config,
//        provider
//    ) {
//
//
//    override fun newRepoSettings(): YakExtRepositorySettings = YakExtRepositorySettings()
//
//    override fun resolverFor(settings: YakExtRepositorySettings): ExtensionArtifactResolver {
//        TODO("Not yet implemented")
//    }
//
//    public class ExtensionArtifactResolver(
//        repository: RepositoryHandler<YakExtDescriptor, YakExtArtifactMetadata, YakExtRepositorySettings>,
//        graphController: GraphController
//    ) : ArtifactResolver<
//            YakExtDescriptor,
//            YakExtArtifactMetadata,
//            YakExtRepositorySettings,
//            YakExtArtifactResolutionOptions>(
//        repository, graphController
//    ) {
//        override fun emptyOptions(): YakExtArtifactResolutionOptions = YakExtArtifactResolutionOptions()
//
//        override fun resolve(
//            meta: YakExtArtifactMetadata,
//            options: YakExtArtifactResolutionOptions,
//            trace: ArtifactRepository.ArtifactTrace?
//        ): Artifact? {
//            TODO("Not yet implemented")
//        }
//    }
//}