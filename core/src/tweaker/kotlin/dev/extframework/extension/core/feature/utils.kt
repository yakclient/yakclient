package dev.extframework.extension.core.feature

import dev.extframework.core.api.feature.DefineFeatures
import dev.extframework.core.api.feature.Feature
import dev.extframework.core.api.feature.ImplementFeatures
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.annotation.AnnotationTarget
import dev.extframework.extension.core.delegate.Delegation
import org.objectweb.asm.tree.ClassNode

public fun ClassNode.definesFeatures(processor: AnnotationProcessor): Boolean =
    processor.process(this, DefineFeatures::class.java).isNotEmpty()

public fun ClassNode.findDefinedFeatures(
    processor: AnnotationProcessor,
): List<FeatureReference> {
    val clsName = name

    val annotations = processor
        .process(this, Feature::class.java)

    val features = annotations
        .map { (annotation, target) ->
            when (target.elementType) {
                AnnotationTarget.ElementType.CLASS -> {
                    if (annotations.size > 1) {
                        throw IllegalFeatureException("Features cannot contain features.") {
                            clsName.replace('/', '.') asContext "Feature container name"

                            solution("Remove the @Feature annotation on \${Feature container name}")
                        }
                    }

                    FeatureReference(
                        annotation.name.takeUnless { it.isBlank() } ?: clsName,
                        clsName,
                        clsName,
                        FeatureType.CLASS
                    )
                }

                AnnotationTarget.ElementType.METHOD -> {
                    val name =
                        annotation.name.takeUnless(String::isBlank) ?: "${clsName}:${target.methodNode.name}"

                    FeatureReference(
                        name,
                        target.methodNode.name + target.methodNode.desc,
                        clsName,
                        FeatureType.METHOD
                    )
                }

                AnnotationTarget.ElementType.FIELD -> {
                    val name = annotation.name
                        .takeUnless(String::isBlank)
                        ?: "${clsName}:${target.fieldNode.name}"

                    FeatureReference(
                        name,
                        target.fieldNode.name + target.fieldNode.desc,
                        clsName,
                        FeatureType.FIELD
                    )
                }

                else -> throw UnsupportedOperationException("Annotation cannot be applied to this target: '${target}'")
            }
        }

    return features
}

public fun ClassNode.implementsFeatures(
    annotationProcessor: AnnotationProcessor,
): Boolean = annotationProcessor.process(this, ImplementFeatures::class.java).isNotEmpty()

public fun ClassNode.findImplementedFeatures(
    thisPartition: String,
    processor: AnnotationProcessor,
    delegation: Delegation
): List<Pair<FeatureReference, String>> {
    val clsName = name

    val annotations = processor.process(this, Feature::class.java)

    return annotations
        .map { (annotation, target: AnnotationTarget) ->
            when (target.elementType) {
                AnnotationTarget.ElementType.CLASS -> {
                    if (annotations.size > 1) {
                        throw IllegalFeatureException("Features cannot contain features.") {
                            clsName.replace('/', '.') asContext "Feature container name"

                            solution("Remove the @Feature annotation on \${Feature container name}")
                        }
                    }

                    val ref = FeatureReference(
                        annotation.name.takeUnless { it.isBlank() } ?: clsName,
                        clsName,
                        clsName,
                        FeatureType.CLASS
                    )

                    ref to (delegation.get(ref, target)?.implementingPartition ?: thisPartition)
                }

                AnnotationTarget.ElementType.METHOD -> {
                    val name =
                        annotation.name.takeUnless(String::isBlank) ?: "${clsName}:${target.methodNode.name}"

                    val ref = FeatureReference(
                        name,
                        target.methodNode.name + target.methodNode.desc,
                        clsName,
                        FeatureType.METHOD
                    )

                    ref to (delegation.get(ref, target)?.implementingPartition ?: thisPartition)
                }

                AnnotationTarget.ElementType.FIELD -> {
                    val name = annotation.name
                        .takeUnless(String::isBlank)
                        ?: "${clsName}:${target.fieldNode.name}"

                    val ref = FeatureReference(
                        name,
                        target.fieldNode.name + target.fieldNode.desc,
                        clsName,
                        FeatureType.FIELD
                    )

                    ref to (delegation.get(ref, target)?.implementingPartition ?: thisPartition)
                }

                else -> throw UnsupportedOperationException("Annotation cannot be applied to this target: '${target}'")
            }
        }
}