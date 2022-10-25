package net.yakclient.plugins.yakclient.extension.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.*

public object ExtensionRepositoryFactory : RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
        return SimpleMavenArtifactRepository(
            this,
            ExtensionMetadataHandler(
                settings
            ),
            settings
        )
    }
}
