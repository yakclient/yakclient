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

    public val partitions: Set<PartitionRuntimeModel>
) {
    public val namedPartitions: Map<String, PartitionRuntimeModel> = partitions.associateBy { it.name }
}

//public data class PartitionModelReference(
//    val type: String,
//    val name: String,
//    val options: Map<String, String>
//)

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
//    val apiVersion: Int,

    val type: String,

    val name: String,

    val repositories: List<ExtensionRepository>,
    val dependencies: Set<Map<String, String>>,

    val options: Map<String, String>
)