package dev.extframework.extension.core.util

import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import java.lang.reflect.Proxy

public fun <T : Annotation> instantiateAnnotation(annotationNode: AnnotationNode, annotationClass: Class<T>): T {
    val valuesMap = annotationNode.createValueMap()

    return instantiateAnnotation(valuesMap, annotationClass)
}

public fun <T : Annotation> instantiateAnnotation(valuesMap: Map<String, Any?>, annotationClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    val annotationInstance = Proxy.newProxyInstance(
        annotationClass.classLoader,
        arrayOf(annotationClass)
    ) { _, method, _ ->
        val value = if (method.name == "toString") "proxy(${annotationClass.name})"
        else valuesMap[method.name] ?: annotationClass.methods.find { it.name == method.name }?.defaultValue

        when(value) {
            is Type -> annotationClass.classLoader.loadClass(value.className)
            else -> value
        }
    } as T

    return annotationInstance
}

public fun createValueMap(values: List<Any?>): Map<String, Any?> {
    val valuesMap = mutableMapOf<String, Any?>()
    for (i in values.indices step 2) {
        valuesMap[values[i] as String] = values[i + 1]
    }

    return valuesMap
}

internal fun AnnotationNode.createValueMap(): Map<String, Any?> {
    val values = values ?: emptyList()

    return createValueMap(values)
}