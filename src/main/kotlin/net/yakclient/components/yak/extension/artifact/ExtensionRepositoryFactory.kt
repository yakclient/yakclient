package net.yakclient.components.yak.extension.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.boot.dependency.DependencyProviders

public class ExtensionRepositoryFactory(
    private val dependencyProviders: DependencyProviders
) : RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
        return SimpleMavenArtifactRepository(
            this,
            ExtensionMetadataHandler(
                settings,
                dependencyProviders
            ),
            settings
        )
    }
}
