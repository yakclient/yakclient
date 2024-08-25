package dev.extframework.extension.core.feature.delegate

import dev.extframework.core.api.feature.Feature
import dev.extframework.extension.core.annotation.AnnotationTarget
import dev.extframework.extension.core.delegate.DelegationProvider
import dev.extframework.extension.core.feature.FeatureReference
import dev.extframework.extension.core.feature.FeatureType
import dev.extframework.extension.core.util.withDots
import dev.extframework.extension.core.util.withSlashes

public class FeatureDelegationProvider : DelegationProvider<FeatureReference> {
    override val targetType: AnnotationTarget.ElementType = AnnotationTarget.ElementType.METHOD
    override val annotationType: Class<out Annotation> = Feature::class.java

    // TODO visit the design in my journal for this
    override fun parseRef(ref: String): FeatureReference {
        val (cls, sig) = ref.split(":")

        val container = cls.withSlashes()
        return FeatureReference(
            "$container:$sig",
            sig,
            container,
            FeatureType.METHOD,
        )
    }

    // TODO make it take the name from @Feature
    override fun parseRef(target: AnnotationTarget): FeatureReference {
        return FeatureReference(
            "${target.classNode.name}:${target.methodNode.name}",
            target.methodNode.name + target.methodNode.desc,
            target.classNode.name,
            FeatureType.METHOD
        )
    }
}