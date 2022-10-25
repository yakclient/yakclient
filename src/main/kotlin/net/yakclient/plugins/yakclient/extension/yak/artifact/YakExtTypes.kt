package net.yakclient.plugins.yakclient.extension.yak.artifact
//
//import com.durganmcbroom.artifact.resolver.ArtifactResolutionOptions
//import com.durganmcbroom.artifact.resolver.RepositoryReference
//import com.durganmcbroom.artifact.resolver.RepositorySettings
//import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactResolutionOptions
//import net.yakclient.plugins.yakclient.extension.yak.YakErmDependency
//import net.yakclient.plugins.yakclient.extension.yak.YakErmRepository
//
//public typealias YakExtArtifactResolutionOptions = SimpleMavenArtifactResolutionOptions
//
//public class YakExtRepositorySettings(
//    _url: String? = null,
//    _ermRepoReferencer: ErmReferencer? = DelegatingErmRepoReferencer()
//) : RepositorySettings() {
//    public var url: String by nullableOrLateInitLocking(_url)
//    public var ermReferencer: ErmReferencer by nullableOrLateInitLocking(_ermRepoReferencer)
//
//    public fun installErmRepoReferencer(referencer: ErmReferencer) {
//        if (ermReferencer is DelegatingErmRepoReferencer) (ermReferencer as DelegatingErmRepoReferencer).delegates.add(
//            referencer
//        )
//        else throw IllegalStateException("Cannot install ERM Repository referencer when the one currently installed cannot delegate!")
//    }
//
//    public fun useYakRepoReferencer() {
//        installErmRepoReferencer { repo ->
//            when (repo.type) {
//                "yak" -> RepositoryReference(YakExtensionRepo, YakExtRepositorySettings())
//                else -> null
//            }
//        }
//    }
//
//    private class DelegatingErmRepoReferencer() : ErmReferencer {
//        val delegates: MutableList<ErmReferencer> = ArrayList()
//
//        override fun reference(repo: YakErmRepository): RepositoryReference<*>? =
//            delegates.firstNotNullOfOrNull { it.reference(repo) }
//    }
//}
