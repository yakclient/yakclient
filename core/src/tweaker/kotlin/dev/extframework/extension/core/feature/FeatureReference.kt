package dev.extframework.extension.core.feature

import dev.extframework.extension.core.delegate.DelegationReference

public data class FeatureReference(
    val name: String,
    // For Method, a Method object
    val reference: String,
    // With slashes
    val container: String,
    val type: FeatureType
) : DelegationReference {
    val qualifiedSignature: String = "$container:$reference"
}

public enum class FeatureType {
    CLASS,
    METHOD,
    FIELD
}