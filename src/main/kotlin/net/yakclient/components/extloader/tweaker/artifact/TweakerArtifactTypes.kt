package net.yakclient.components.extloader.tweaker.artifact

import com.durganmcbroom.artifact.resolver.CheckedResource
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenChildInfo
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import net.yakclient.components.extloader.api.tweaker.TweakerRuntimeModel


internal typealias TweakerChildInfo = SimpleMavenChildInfo

internal class TweakerArtifactMetadata(
    desc: SimpleMavenDescriptor,
    resource: CheckedResource?,
    children: List<TweakerChildInfo>,
) : SimpleMavenArtifactMetadata(desc, resource, children)