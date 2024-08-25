package dev.extframework.extension.core.minecraft.remap

import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.transform.ClassInheritanceTree
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.core.api.mixin.Mixin
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.annotation.AnnotationTarget
import dev.extframework.extension.core.util.createValueMap
import dev.extframework.extension.core.util.instantiateAnnotation
import dev.extframework.extension.core.util.withSlashes
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode

public abstract class InjectionRemapper<T : Annotation>(
    private val annotationProcessor: AnnotationProcessor,
    private val annotation: Class<T>
) : ExtensionRemapper {

    public fun makeAnnotation(
        vararg values: Any
    ): T {
        return instantiateAnnotation(
            createValueMap(values.toList()),
            annotation
        )
    }

    // TODO do we need the inheritance tree?
    public abstract fun remap(
        annotation: T,

        destClass: String,

        mappings: ArchiveMapping,
        source: String,
        target: String,
    ): T

    // TODO this will not preserve order on annotations
    override fun remap(
        mappings: ArchiveMapping,
        inheritanceTree: ClassInheritanceTree,
        source: String,
        target: String
    ): TransformerConfig = TransformerConfig.of {
        transformClass { classNode ->
            val mixinAnno = (classNode.visibleAnnotations + classNode.visibleTypeAnnotations).find {
                it.desc == Type.getType(Mixin::class.java).descriptor
            }
            if (mixinAnno != null) {

                val annotations = annotationProcessor.process(classNode, annotation)

                fun <A : Annotation> AnnotationVisitor.visit(annotation: A) {
                    annotation::class.java.declaredMethods.forEach {
                        val value = it.invoke(this)
                        val name = it.name

                        when (value) {
                            is Annotation -> {
                                val visitedAnno = visitAnnotation(name, Type.getType(value::class.java).descriptor)
                                visitedAnno.visit(annotation)
                            }

                            is Enum<*> -> {
                                // TODO is this right?
                                visitEnum(name, Type.getType(value::class.java).descriptor, value.name)
                            }
                            // TODO support for arrays? It appears that the following 3 lines will do this but im not sure
                            else -> {
                                visit(name, value)
                            }
                        }
                    }
                }

                val annotationType = Type.getType(this::class.java)

                annotations
                    .asSequence()
                    .map {
                        val remappedAnnotation = remap(
                            it.annotation,
                            instantiateAnnotation(mixinAnno, Mixin::class.java).value.java.name.withSlashes(),
                            mappings,
                            source,
                            target,
                        )

                        val node = AnnotationNode(annotationType.descriptor)

                        node.visit(remappedAnnotation)

                        node to it.target
                    }
                    .groupBy { it.second }
                    .mapValues { it.value.map { it.first } }
                    .forEach { (target, it: List<AnnotationNode>) ->
                        when (target.elementType) {
                            AnnotationTarget.ElementType.CLASS -> {
                                target.classNode.visibleAnnotations.removeIf {
                                    it.desc == annotationType.descriptor
                                }
                                target.classNode.visibleAnnotations.addAll(it)
                            }

                            AnnotationTarget.ElementType.METHOD -> {
                                target.methodNode.visibleAnnotations.removeIf {
                                    it.desc == annotationType.descriptor
                                }
                                target.methodNode.visibleAnnotations.addAll(it)
                            }

                            AnnotationTarget.ElementType.FIELD -> {
                                target.fieldNode.visibleAnnotations.removeIf {
                                    it.desc == annotationType.descriptor
                                }
                                target.fieldNode.visibleAnnotations.addAll(it)
                            }

                            AnnotationTarget.ElementType.PARAMETER -> {
                                target.methodNode.visibleParameterAnnotations[target.parameter].removeIf {
                                    it.desc == annotationType.descriptor
                                }
                                target.methodNode.visibleParameterAnnotations[target.parameter].addAll(it)
                            }
                        }
                    }
            }

            classNode
        }
    }
}