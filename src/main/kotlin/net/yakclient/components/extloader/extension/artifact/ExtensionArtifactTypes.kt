package net.yakclient.components.extloader.extension.artifact

import com.durganmcbroom.artifact.resolver.CheckedResource
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel

public typealias ExtensionArtifactRequest = SimpleMavenArtifactRequest

public typealias ExtensionRepositorySettings = SimpleMavenRepositorySettings

public typealias ExtensionStub = SimpleMavenArtifactStub

public typealias ExtensionArtifactReference = SimpleMavenArtifactReference

public typealias ExtensionDescriptor = SimpleMavenDescriptor

public typealias ExtensionChildInfo = SimpleMavenChildInfo

public typealias ExtensionArtifactStub = SimpleMavenArtifactStub



public class ExtensionArtifactMetadata(
    desc: SimpleMavenDescriptor,
    resource: CheckedResource?,
    children: List<ExtensionChildInfo>,
    public val erm: ExtensionRuntimeModel,
) : SimpleMavenArtifactMetadata(desc, resource, children)

