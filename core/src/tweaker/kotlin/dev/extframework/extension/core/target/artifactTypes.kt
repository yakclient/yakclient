package dev.extframework.extension.core.target

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings

public object TargetDescriptor : ArtifactMetadata.Descriptor {
    override val name: String = "target"
}

public object TargetArtifactRequest : ArtifactRequest<TargetDescriptor> {
    override val descriptor: TargetDescriptor = TargetDescriptor
}

public object TargetRepositorySettings : RepositorySettings