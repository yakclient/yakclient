package net.yakclient.components.extloader.extension.feature

import net.yakclient.client.api.annotation.Feature
import net.yakclient.client.api.annotation.FeatureContainer
import net.yakclient.components.extloader.util.instantiateAnnotation
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.IllegalStateException

internal fun ClassNode.containsFeatures(): Boolean = visibleAnnotations?.any { a ->
    Type.getType(a.desc) == Type.getType(FeatureContainer::class.java)
} ?: false

internal fun ClassNode.findFeatures(): List<FeatureReference> {
    val clsName = name

    val fieldFeatures = fields.mapNotNull {
        val anno = it.visibleAnnotations?.find { a ->
            Type.getType(a.desc) == Type.getType(Feature::class.java)
        }?.let { instantiateAnnotation(it, Feature::class.java) } ?: return@mapNotNull null

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
            Type.getType(a.desc) == Type.getType(Feature::class.java)
        }?.let { instantiateAnnotation(it, Feature::class.java) } ?: return@mapNotNull null

        val name = anno.name.takeUnless { it.isBlank() } ?: "${clsName}:${it.name}"

        FeatureReference(
            name,
            it.name + it.desc,
            clsName,
            FeatureType.METHOD
        )
    }

    val clsFeature = visibleAnnotations?.find { a ->
        Type.getType(a.desc) == Type.getType(Feature::class.java)
    }?.let { instantiateAnnotation(it, Feature::class.java) } ?: return fieldFeatures + methodFeatures

    if (fieldFeatures.isNotEmpty() || methodFeatures.isNotEmpty()) {
        throw IllegalFeatureException("Feature container: '$clsName' is a feature, yet also contains features; features cannot contain features.")
    }

    return listOf(
        FeatureReference(clsFeature.name.takeUnless { it.isBlank() } ?: clsName, clsName, clsName, FeatureType.CLASS)
    )
}