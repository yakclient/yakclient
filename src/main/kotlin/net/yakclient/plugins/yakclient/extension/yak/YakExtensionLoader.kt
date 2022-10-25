package net.yakclient.plugins.yakclient.extension.yak

private const val MANIFEST_LOCATION = "extension.json"
//
//internal class YakExtensionLoader(
//    private val dependencyManager: YakExtManifestDependencyManager,
//    private val graph: ExtensionGraph
//) : ExtensionLoader<YakExtensionInfo> {
//    private val mapper = ObjectMapper().registerModule(KotlinModule())
//
//    override fun load(info: YakExtensionInfo): ExtensionProcess {
//        val loader = ExtensionClassLoader()
//
//        val result = Archives.resolve(
//            info.archive,
//            loader,
//            Archives.Resolvers.JPM_RESOLVER,
////            setOf(info.parent.handle)
//        )
//
//        val archive = result.archive
//
//        val extensionClass = archive.classloader.loadClass(info.erm.extensionClass)
//        val extension = extensionClass.getConstructor().newInstance() as Extension
//
//        extension.init(archive)
//
//        return ExtensionProcess(
//            extension,
//            loadStateHolder(info.reference, archive, info.erm)
//        )
//    }
//
//    private fun loadStateHolder(
//        ref: ArchiveReference,
//        handle: ArchiveHandle,
//        erm: YakErm
//    ): ExtensionStateHolder {
//        if (manifest.stateHolderClass != null) {
//            TODO()
//        } else {
//            TODO()
//        }
//    }
//
////    override fun loadInfo(path: Path): YakExtensionInfo {
////        val archive = Archives.Finders.JPM_FINDER.find(path)
////
////        val manifestEntry =
////            archive.reader[MANIFEST_LOCATION] ?: throw IllegalArgumentException("Manifest not found in extension")
////        val manifest = mapper.readValue<YakErm>(manifestEntry.resource.open())
////
////        val dependencies = loadDependencies(manifest)
////
////        return YakExtensionInfo(
////            archive,
////            dependencies.toSet(),
////            manifest
////        )
////    }
//
//    private fun loadDependencies(manifest: YakErm): List<ArchiveHandle> {
//        val group = ArtifactGraph(ResolutionGroup) {
//            manifest.repositories.forEach {
//                dependencyManager.registerWith(this, it)
//            }
//        }
//
//        val loaders = manifest.repositories.map { r ->
//            val repoGraph =
//                (group[dependencyManager.getProvider(r)] as? ArtifactGraph<*, RepositorySettings, ArtifactGraph.ArtifactResolver<*, *, RepositorySettings, ArtifactResolutionOptions>>)
//                    ?: throw IllegalArgumentException("Unknown provider type: '${r.type}'.")
//
//
//            val loader = graph.createLoader(repoGraph).configureRepository(dependencyManager.getRepositorySettings(r))
//            loader to r
//        }
//
//        val dependencies = manifest.dependencies.flatMap { d ->
//            loaders.firstNotNullOfOrNull { (loader, repo) ->
//                loader.load(
//                    d.notation,
//                    dependencyManager.getArtifactResolutionOptions(repo, d)
//                )
//            } ?: throw IllegalArgumentException("Failed to find dependency: '${d.notation}'")
//        }
//
//        return dependencies
//    }
//}