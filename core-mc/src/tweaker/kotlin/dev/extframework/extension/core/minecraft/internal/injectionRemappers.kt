package dev.extframework.extension.core.minecraft.internal

import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.transform.mapMethodDesc
import dev.extframework.archive.mapper.transform.mapMethodName
import dev.extframework.archives.extension.Method
import dev.extframework.core.api.mixin.SourceInjection
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.minecraft.remap.InjectionRemapper

internal class SourceInjectionRemapper(annotationProcessor: AnnotationProcessor) : InjectionRemapper<SourceInjection>(
    annotationProcessor, SourceInjection::class.java
) {
    override fun remap(
        annotation: SourceInjection,
        destClass: String,
        mappings: ArchiveMapping,
        source: String,
        target: String,
    ): SourceInjection {
        return SourceInjection(
            point = annotation.point,
            methodTo = Method(annotation.methodTo).let {
                val name = mappings.mapMethodName(destClass, it.name, it.descriptor, source, target) ?: it.name

                val desc = mappings.mapMethodDesc(it.descriptor, source, target)

                name + desc
            },
            priority = annotation.priority,
        )
    }
}