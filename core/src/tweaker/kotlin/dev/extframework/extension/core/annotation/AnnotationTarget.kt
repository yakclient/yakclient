package dev.extframework.extension.core.annotation

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

public data class AnnotationTarget(
    public val elementType: ElementType,

    public val classNode: ClassNode,
    private val _methodNode: MethodNode? = null,
    private val _fieldNode: FieldNode? = null,
    private val _parameter: Int? = null,
) {
    public val methodNode: MethodNode
        get() = if (elementType == ElementType.METHOD) _methodNode!! else unexpectedElement(ElementType.METHOD)
    public val fieldNode: FieldNode
        get() = if (elementType == ElementType.FIELD) _fieldNode!! else unexpectedElement(ElementType.FIELD)
    public val parameter: Int // Index of the parameter
        get() = if (elementType == ElementType.CLASS) _parameter!! else unexpectedElement(ElementType.CLASS)

    public enum class ElementType {
        CLASS,
        METHOD,
        FIELD,
        PARAMETER,
    }

    private companion object {
        private fun unexpectedElement(expected: ElementType): Nothing {
            throw IllegalArgumentException("Expected to have an element of type '${expected.name}', but it was not provided.")
        }
    }
}