package dev.extframework.extension.core.mixin

import com.durganmcbroom.jobs.Job
import dev.extframework.archives.mixin.MixinInjection
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import org.objectweb.asm.tree.ClassNode

public interface MixinInjectionProvider<A : Annotation, T : MixinInjection.InjectionData> {
    public val type: String
    public val annotationType: Class<A>

    public fun parseData(context: InjectionContext<A>): Job<T>

    public fun get(): MixinInjection<T>

    public data class InjectionContext<out A : Annotation>(
        val element: AnnotationProcessor.AnnotationElement<A>,
        val targetNode: ClassNode,
        val extension: ExtensionDescriptor
    )
}