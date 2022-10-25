package net.yakclient.plugins.yakclient.extension.yak.artifact
//
//import com.durganmcbroom.artifact.resolver.*
//
//public class YakExtArtifactGraph(
//    config: YakExtArtifactGraphConfig,
//    provider: ArtifactGraphProvider<YakExtArtifactGraphConfig, *>
//) : ArtifactGraph<YakExtArtifactGraphConfig, YakExtRepositorySettings, YakExtArtifactGraph.YakExtArtifactResolver>(
//    config, provider
//) {
//    override fun newRepoSettings(): YakExtRepositorySettings = YakExtRepositorySettings()
//
//    override fun resolverFor(settings: YakExtRepositorySettings): YakExtArtifactResolver = YakExtArtifactResolver(
//        YakExtRepositoryHandler(settings), config.graph,
//        config.deReferencer
//    )
//
//    public class YakExtArtifactResolver(
//        repository: YakExtRepositoryHandler,
//        graphController: GraphController,
//        private val dereferencer: RepositoryDeReferencer<YakExtDescriptor, YakExtArtifactResolutionOptions>
//    ) :
//        ArtifactResolver<YakExtDescriptor, YakExtArtifactMetadata, YakExtRepositorySettings, YakExtArtifactResolutionOptions>(
//            repository, graphController
//        ) {
//        override fun emptyOptions(): YakExtArtifactResolutionOptions = YakExtArtifactResolutionOptions()
//
//        override fun resolve(
//            meta: YakExtArtifactMetadata,
//            options: YakExtArtifactResolutionOptions,
//            trace: ArtifactRepository.ArtifactTrace?
//        ): Artifact {
//            val extDeps: List<Artifact> = meta.transitives.map {
//                it.resolutionCandidates.mapNotNull(dereferencer::deReference).firstNotNullOfOrNull { r ->
//                    r.artifactOf(it.desc, options, trace)
//                } ?: throw IllegalArgumentException("Failed to resolve extension dependency: '${it.desc}' of extension: '${meta.desc}'.")
//            }
//
//            val deps: List<Artifact> = meta.dependencies.map { i ->
//                i.resolutionCandidates.firstNotNullOfOrNull { r ->
//                    val repo = dereferencer.deReference(r.resolutionCandidate) ?: return@firstNotNullOfOrNull null
//                    repo.artifactOf(i.desc, r.options, trace)
//                } ?: throw IllegalArgumentException("Failed to resolve dependency: '${i.desc}' of extension: '${meta.desc}'.")
//            }
//
//            return Artifact(
//                meta,
//                extDeps + deps
//            )
//        }
//    }
//}
