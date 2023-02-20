package net.yakclient.components.yak.extension

import net.yakclient.components.yak.mixin.InjectionPriorities

public data class ExtensionMetadata(
    val erm: ExtensionRuntimeModel,
    val mixins: List<ExtensionMixin>
)


// TODO make jackson parsing return a more readable yak wrapped error.
// Represents the YakClient ERM (or Extension runtime model)
public data class ExtensionRuntimeModel(
    val groupId: String,
    val name: String,
    val version: String,

    val packagingType: String, // Jar, War, Zip, etc...

    val extensionClass: String,

    val dependencyRepositories: List<ErmRepository> = ArrayList(),
    val dependencies: List<Map<String, String>> = ArrayList(),

    val extensionRepositories: List<Map<String, String>> = ArrayList(),
    val extensions: List<Map<String, String>> = ArrayList(),

    val versioningPartitions: Map<String, List<String>> // Versions to partition
)

public data class ErmRepository(
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

