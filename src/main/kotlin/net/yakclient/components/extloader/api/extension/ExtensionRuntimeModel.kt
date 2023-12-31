package net.yakclient.components.extloader.api.extension

import net.yakclient.client.api.InjectionPriorities

// TODO make jackson parsing return a more readable yak wrapped error.
// Represents the YakClient ERM (or Extension runtime model)
public data class ExtensionRuntimeModel(
    val groupId: String,
    val name: String,
    val version: String,

    val packagingType: String, // Jar, War, Zip, etc...

    val extensionClass: String,
    val mainPartition: MainVersionPartition,

    val extensionRepositories: List<Map<String, String>> = ArrayList(),
    val extensions: List<Map<String, String>> = ArrayList(),

    val versionPartitions: List<ExtensionVersionPartition>,

//        val environmentTweakerRepositories: List<ExtensionRepository>,
//        val environmentTweakers: List<Map<String, String>>,
    val tweakerPartition: ExtensionTweakerPartition?,
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

public interface ExtensionPartition {
        public val name: String
        public val path: String
        public val repositories: List<ExtensionRepository>
        public val dependencies: List<Map<String, String>>
}

public data class MainVersionPartition(
    override val name: String,
    override val path: String,
    override val repositories: List<ExtensionRepository>,
    override val dependencies: List<Map<String, String>>
) : ExtensionPartition

public data class ExtensionVersionPartition(
    override val name: String,
    override val path: String,

    val mappingNamespace: String,
    val supportedVersions: Set<String>,

    override val repositories: List<ExtensionRepository>,
    override val dependencies: List<Map<String, String>>,

//    val mixins: List<ExtensionMixin>,
) : ExtensionPartition

public data class ExtensionTweakerPartition(
    override val path: String,

    override val repositories: List<ExtensionRepository>,
    override val dependencies: List<Map<String, String>>,

    val entrypoint: String
) : ExtensionPartition {
        override val name: String = "tweaker"
}

//public data class ExtensionPartitionMappingReference(
//        val type: String,
//)