package net.yakclient.plugins.yakclient.extension


// Represents the YakClient ERM (or Extension runtime model)
public data class ExtensionRuntimeModel(
    val groupId: String,
    val name: String,
    val version: String,

    val packagingType: String, // Jar, War, Zip, etc...

    val extensionClass: String,
    val stateHolderClass: String?,

    val dependencyRepositories: List<ErmRepository>,
    val dependencies: List<Map<String, String>>,

    val extensionRepositories: List<Map<String, String>>,
    val extensions: List<Map<String, String>>,
)

public data class ErmRepository(
    val type: String,
    val settings: Map<String, String>
)

//public data class ErmDependency(
//    val type: String,
//    val request: Map<String, String>
//)

//public data class YakErmDependency(
//    val notation: String,
//    val options: Options
//) {
//    public data class Options(
//        val isTransitive: Boolean,
//        val exclude: Set<String>,
//    )
//}
//
//public data class YakErmRepository(
//    val type: String,
//    val configuration: Configuration
//) {
//    public data class Configuration(
//        val url: String?
//    )
//}