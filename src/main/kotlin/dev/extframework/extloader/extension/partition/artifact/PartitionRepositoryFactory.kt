package dev.extframework.extloader.extension.partition.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.extloader.extension.artifact.ExtensionRepositoryFactory
import dev.extframework.tooling.api.extension.ExtensionRepository
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import io.ktor.client.request.request

public class PartitionRepositoryFactory(
    private val prmProvider: (PartitionDescriptor, ExtensionRepositorySettings) -> PartitionRuntimeModel?,
) : RepositoryFactory<ExtensionRepositorySettings, PartitionArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): PartitionArtifactRepository {
        return PartitionArtifactRepository(
            settings,
            prmProvider,
            this
        )
    }
}