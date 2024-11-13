package dev.extframework.extension.core.delegate

import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey
import dev.extframework.extension.core.annotation.AnnotationTarget

/**
 * Represents the concept of code delegation between 2 features
 * where 1 feature may define a concept that another wishes
 * to use as well. This is handy when targeting different versions
 * of an application and some logic changes between versions but
 * other logic doesn't.
 */
public interface Delegation : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = Delegation

    public companion object : EnvironmentAttributeKey<Delegation>

    public fun <T: DelegationReference> get(
        ref: T,
        target: AnnotationTarget
    ) : Delegated<T>?

    public fun isDelegated(
        ref: DelegationReference,
        target: AnnotationTarget
    ): Boolean = get(ref, target) != null
}