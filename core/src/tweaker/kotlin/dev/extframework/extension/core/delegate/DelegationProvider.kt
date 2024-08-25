package dev.extframework.extension.core.delegate

import dev.extframework.extension.core.annotation.AnnotationTarget

public interface DelegationProvider<out R: DelegationReference> {
    public val targetType: AnnotationTarget.ElementType
    public val annotationType: Class<out Annotation>

    public fun parseRef(ref: String): R

    public fun parseRef(target: AnnotationTarget): R
}