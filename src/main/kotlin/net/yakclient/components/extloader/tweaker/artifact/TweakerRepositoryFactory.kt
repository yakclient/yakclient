package net.yakclient.components.extloader.tweaker.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.*

internal object TweakerRepositoryFactory : RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
        return SimpleMavenArtifactRepository(
            this,
            TweakerMetadataHandler(
                settings
            ),
            settings
        )
    }
}
