package dev.extframework.tooling.api.extension

import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor

// TODO make jackson parsing return a more readable yak wrapped error.
// Represents the YakClient ERM (or Extension runtime model)

public data class ExtensionRuntimeModel(
    val apiVersion: Int,
    val groupId: String,
    val name: String,
    val version: String,

    val repositories: List<Map<String, String>> = ArrayList(),
    val parents: Set<ExtensionParent> = HashSet(),

    public val partitions: Set<PartitionModelReference>
)

public data class PartitionModelReference(
    val type: String,
    val name: String
)

public data class ExtensionParent(
    val group: String,
    val extension: String,
    val version: String
) {
    public fun toDescriptor() : ExtensionDescriptor {
        return ExtensionDescriptor(group, extension, version)
    }

    override fun toString(): String =toDescriptor().toString()
}

public val ExtensionRuntimeModel.descriptor : ExtensionDescriptor
    get() = ExtensionDescriptor.parseDescriptor("$groupId:$name:$version")

public data class ExtensionRepository(
    val type: String,
    val settings: Map<String, String>
)

public data class PartitionRuntimeModel(
    public val type: String,

    public val name: String,
//    public val path: String,

    public val repositories: List<ExtensionRepository>,
    public val dependencies: Set<Map<String, String>>,

    public val options: Map<String, String>
)