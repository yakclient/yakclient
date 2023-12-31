package net.yakclient.components.extloader.tweaker.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.components.extloader.extension.artifact.extRepositoryStubResolver

internal class TweakerRepositoryFactory(
    private val providers: DependencyTypeContainer
) : RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
        return object : SimpleMavenArtifactRepository(
            this,
            TweakerMetadataHandler(
                settings,
                providers
            ),
            settings
        ) {
            override val stubResolver: SimpleMavenArtifactStubResolver = SimpleMavenArtifactStubResolver(
                extRepositoryStubResolver(settings),
                this@TweakerRepositoryFactory
            )
        }
    }
}
