package dev.extframework.components.extloader.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.components.extloader.extension.artifact.ExtensionRepositoryFactory
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings

public class PartitionRepositoryFactory(
    private val extensionRepositoryFactory: ExtensionRepositoryFactory
) : RepositoryFactory<ExtensionRepositorySettings, PartitionArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): PartitionArtifactRepository {
        return PartitionArtifactRepository(
            settings,
            this,
            extensionRepositoryFactory.createNew(settings)
        )
    }
}