package net.yakclient.components.extloader.util

import org.objectweb.asm.tree.AnnotationNode
import java.lang.reflect.Proxy

internal fun <T : Annotation> instantiateAnnotation(annotationNode: AnnotationNode, annotationClass: Class<T>): T {
    val values = annotationNode.values ?: emptyList()

    val valuesMap = mutableMapOf<String, Any?>()
    for (i in values.indices step 2) {
        valuesMap[values[i] as String] = values[i + 1]
    }

    @Suppress("UNCHECKED_CAST")
    val annotationInstance = Proxy.newProxyInstance(
        annotationClass.classLoader,
        arrayOf(annotationClass)
    ) { _, method, _ ->
        if (method.name == "toString") "proxy(${annotationClass.name})"
        else valuesMap[method.name] ?: annotationClass.methods.find { it.name == method.name }?.defaultValue
    } as T

    return annotationInstance
}