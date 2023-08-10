package net.yakclient.components.extloader.extension.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.boot.dependency.DependencyTypeContainer

public class ExtensionRepositoryFactory(
    private val dependencyProviders: DependencyTypeContainer
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
