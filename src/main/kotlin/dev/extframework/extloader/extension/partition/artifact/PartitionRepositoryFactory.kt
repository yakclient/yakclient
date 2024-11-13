package dev.extframework.extloader.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.extloader.extension.artifact.ExtensionRepositoryFactory
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings

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