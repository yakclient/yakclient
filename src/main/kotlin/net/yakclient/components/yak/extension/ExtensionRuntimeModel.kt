package net.yakclient.components.yak.extension

import net.yakclient.components.yak.mixin.InjectionPriorities

// TODO make jackson parsing return a more readable yak wrapped error.
// Represents the YakClient ERM (or Extension runtime model)
public data class ExtensionRuntimeModel(
    val groupId: String,
    val name: String,
    val version: String,

    val packagingType: String, // Jar, War, Zip, etc...

    val extensionClass: String,
    val mainPartition: String, // its name

    val extensionRepositories: List<Map<String, String>> = ArrayList(),
    val extensions: List<Map<String, String>> = ArrayList(),

    val versionPartitions: List<ExtensionVersionPartition>,
)

public data class ExtensionRepository(
    val type: String,
    val settings: Map<String, String>
)

public data class ExtensionMixin(
    val classname: String,
    val destination: String,
    val injections: List<ExtensionInjection>
)

public data class ExtensionInjection(
    val type: String,
    val options: Map<String, String>,
    val priority: Int = InjectionPriorities.DEFAULT
)

public data class ExtensionVersionPartition(
    val name: String,
    val path: String,

    val supportedVersions: Set<String>,

    val repositories: List<ExtensionRepository>,
    val dependencies: List<Map<String, String>>,

    val mixins: List<ExtensionMixin>
)

