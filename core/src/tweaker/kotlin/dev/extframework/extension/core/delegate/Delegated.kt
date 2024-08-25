package dev.extframework.extension.core.delegate

public data class Delegated<T: DelegationReference>(
    // The partition that has the @Delegate annotation
    val delegatingRef: T,
//    val delegatingPartition: String,

    // The feature that is on the receiving end of the @Delegate annotation
    val implementingRef: T,
    val implementingPartition: String
)
