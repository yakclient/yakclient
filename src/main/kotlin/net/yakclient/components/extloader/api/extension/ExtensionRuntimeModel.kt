package net.yakclient.components.extloader.api.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor

// TODO make jackson parsing return a more readable yak wrapped error.
// Represents the YakClient ERM (or Extension runtime model)

public data class ExtensionRuntimeModel(
    val groupId: String,
    val name: String,
    val version: String,

    val packagingType: String, // Jar, War, Zip, etc...

    val extensionRepositories: List<Map<String, String>> = ArrayList(),
    val extensions: Set<Map<String, String>> = HashSet(),

    public val partitions: Set<ExtensionPartition>
)

public val ExtensionRuntimeModel.descriptor : ExtensionDescriptor
    get() = ExtensionDescriptor.parseDescription("$groupId:$name:$version")!!

public data class ExtensionRepository(
    val type: String,
    val settings: Map<String, String>
)

public data class ExtensionPartition(
    public val type: String,

    public val name: String,
    public val path: String,

    public val repositories: List<ExtensionRepository>,
    public val dependencies: Set<Map<String, String>>,

    public val options: Map<String, String>
)