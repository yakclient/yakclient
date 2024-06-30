package dev.extframework.components.extloader.extension.feature

public data class FeatureReference(
    val name: String,
    // For Method, a Method object
    val signature: String,
    // With slashes
    val container: String,
    val type: FeatureType
) {
    val qualifiedSignature: String = "$container:$signature"
}

public enum class FeatureType {
    CLASS,
    METHOD,
    FIELD
}