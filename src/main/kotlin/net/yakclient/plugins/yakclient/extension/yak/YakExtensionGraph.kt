package net.yakclient.plugins.yakclient.extension.yak

//public class YakExtensionGraph(
//    path: Path,
//    private val dependencyGraph: DependencyGraph
//) : ExtensionGraph<YakExtensionNode, YakExtensionData>(ExtensionStore(YakExtensionDataAccess(path))) {
//
//
//
//    private inner class YakExtGraphPopulator(
//        loader: ExtensionLoader<YakExtensionInfo>
//    ) : ExtensionGraphPopulator<YakExtArtifactResolutionOptions, YakExtensionInfo>(resolver, loader) {
//
//
//        //            check(artifact.metadata is YakExtArtifactMetadata) { "Invalid artifact! The base artifact must be a YakClient extension!" }
//        //
//        //            val (c, d) = artifact.children.partition { it.metadata is YakExtArtifactMetadata }
//        //
//        //            val dependencies = d.map { dependencyGraph.load(it) }
//        //            val children = c.map(::load)
//        //
//        //            val info =
//        //            val process = loader.load()
//        override fun load(name: String, options: YakExtArtifactResolutionOptions): YakExtensionNode? {
//            TODO("Not yet implemented")
//        }
//
//        private fun cache(artifact: Artifact) {
//            val meta = artifact.metadata as? YakExtArtifactMetadata
//                ?: throw IllegalArgumentException("Invalid artifact: '$artifact'. Its metadata type must be '${YakExtArtifactMetadata::class.simpleName}'.")
//
//            val key = DescriptorKey(meta.desc)
//            if (store.contains(key)) return
//
//            val erm = meta.erm
//            store.put(
//                key,
//                YakExtensionData(
//                    key,
//                    erm.extensionDependencies.map {  }
//                )
//            )
//        }
//    }
//}