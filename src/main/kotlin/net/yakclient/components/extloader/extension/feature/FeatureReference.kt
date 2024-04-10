package net.yakclient.components.extloader.extension.feature

public data class FeatureReference(
    val name: String,
    val signature: String,
    val container: String,
    val type: FeatureType
)

public enum class FeatureType {
    CLASS,
    METHOD,
    FIELD
}