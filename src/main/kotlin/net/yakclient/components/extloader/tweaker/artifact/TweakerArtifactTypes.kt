package net.yakclient.components.extloader.tweaker.artifact

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenChildInfo
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.resources.Resource


internal typealias TweakerChildInfo = SimpleMavenChildInfo

internal class TweakerArtifactMetadata(
    desc: SimpleMavenDescriptor,
    resource: Resource?,
    children: List<TweakerChildInfo>,
) : SimpleMavenArtifactMetadata(desc, resource, children)