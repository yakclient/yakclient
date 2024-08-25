package dev.extframework.extension.core.annotation

import dev.extframework.archives.ArchiveReference
import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey
import org.objectweb.asm.tree.ClassNode

public interface AnnotationProcessor : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = AnnotationProcessor

    public companion object : EnvironmentAttributeKey<AnnotationProcessor>

    public fun <T: Annotation> process(
        archive: ArchiveReference,
        annotation: Class<T>,
    ) : List<AnnotationElement<T>>

    public fun <T: Annotation> process(
        classNode: ClassNode,
        annotation: Class<T>,
    ): List<AnnotationElement<T>>

    public data class AnnotationElement<out T: Annotation>(
        val annotation: T,
        val target: AnnotationTarget,
    )
}