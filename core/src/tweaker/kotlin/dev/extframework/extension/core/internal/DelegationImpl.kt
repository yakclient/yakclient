package dev.extframework.extension.core.internal

import dev.extframework.core.api.delegate.Delegate
import dev.extframework.extension.core.annotation.AnnotationTarget
import dev.extframework.extension.core.delegate.Delegated
import dev.extframework.extension.core.delegate.Delegation
import dev.extframework.extension.core.delegate.DelegationProvider
import dev.extframework.extension.core.delegate.DelegationReference
import dev.extframework.extension.core.util.instantiateAnnotation
import dev.extframework.tooling.api.environment.MutableObjectSetAttribute
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode

internal class DelegationImpl(
    val providers: MutableObjectSetAttribute<DelegationProvider<*>>
) : Delegation {
    private fun AnnotationTarget.annotations(): List<AnnotationNode> {
        return when (elementType) {
            AnnotationTarget.ElementType.CLASS -> classNode.visibleAnnotations ?: listOf()
            AnnotationTarget.ElementType.METHOD -> methodNode.visibleAnnotations
                ?: listOf()

            AnnotationTarget.ElementType.FIELD -> fieldNode.visibleAnnotations ?: listOf()
            AnnotationTarget.ElementType.PARAMETER -> {
                (methodNode.visibleParameterAnnotations ?: arrayOf())[parameter] ?: listOf()
            }
        }
    }

    override fun <T : DelegationReference> get(
        ref: T,
        target: AnnotationTarget
    ): Delegated<T>? {
        val annotations = target.annotations()
        val delegateAnnotation = (annotations.find {
            it.desc == Type.getType(Delegate::class.java).descriptor
        } ?: return null).let { instantiateAnnotation(it, Delegate::class.java) }

        return providers.filter { provider ->
            provider.targetType == target.elementType && annotations.any {
                it.desc == Type.getType(provider.annotationType).descriptor
            }
        }.firstNotNullOfOrNull { provider ->
            if (provider.parseRef(target) != ref) return@firstNotNullOfOrNull null

            Delegated(
                ref,
                delegateAnnotation.ref.takeIf { it.isNotEmpty() }?.let(provider::parseRef) ?: ref,
                delegateAnnotation.value
            ) as Delegated<T>
        }
    }
}