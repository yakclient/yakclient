package dev.extframework.components.extloader.extension.feature

import dev.extframework.components.extloader.util.instantiateAnnotation
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

internal fun ClassNode.containsFeatures(): Boolean = visibleAnnotations?.any { a ->
    Type.getType(a.desc) == Type.getType(dev.extframework.client.api.annotation.FeatureContainer::class.java)
} ?: false

internal fun ClassNode.findFeatures(): List<FeatureReference> {
    val clsName = name

    val fieldFeatures = fields.mapNotNull {
        val anno = it.visibleAnnotations?.find { a ->
            Type.getType(a.desc) == Type.getType(dev.extframework.client.api.annotation.Feature::class.java)
        }?.let { instantiateAnnotation(it, dev.extframework.client.api.annotation.Feature::class.java) } ?: return@mapNotNull null

        val name = anno.name.takeUnless { it.isBlank() } ?: "${clsName}:${it.name}"

        FeatureReference(
            name,
            it.name + it.desc,
            clsName,
            FeatureType.FIELD
        )
    }

    val methodFeatures = methods.mapNotNull {
        val anno = it.visibleAnnotations?.find { a ->
            Type.getType(a.desc) == Type.getType(dev.extframework.client.api.annotation.Feature::class.java)
        }?.let { instantiateAnnotation(it, dev.extframework.client.api.annotation.Feature::class.java) } ?: return@mapNotNull null

        val name = anno.name.takeUnless { it.isBlank() } ?: "${clsName}:${it.name}"

        FeatureReference(
            name,
            it.name + it.desc,
            clsName,
            FeatureType.METHOD
        )
    }

    val clsFeature = visibleAnnotations?.find { a ->
        Type.getType(a.desc) == Type.getType(dev.extframework.client.api.annotation.Feature::class.java)
    }?.let { instantiateAnnotation(it, dev.extframework.client.api.annotation.Feature::class.java) } ?: return fieldFeatures + methodFeatures

    if (fieldFeatures.isNotEmpty() || methodFeatures.isNotEmpty()) {
        throw IllegalFeatureException("Features cannot contain features.") {
            clsName.replace('/', '.') asContext "Feature container name"

            solution("Remove the @Feature annotation on \${Feature container name}")
        }
    }

    return listOf(
        FeatureReference(clsFeature.name.takeUnless { it.isBlank() } ?: clsName, clsName, clsName, FeatureType.CLASS)
    )
}