package dev.extframework.extension.core.mixin

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.mixin.MixinInjection
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.`object`.MutableObjectContainer
import org.objectweb.asm.tree.ClassNode


public data class ProcessedMixinContext<A : Annotation, T : MixinInjection.InjectionData>(
    val provider: MixinInjectionProvider<A, T>,
    val context: MixinInjectionProvider.InjectionContext<A>
) {
    private fun parseData(): Job<T> = provider.parseData(
        context,
    )

    public fun createTransactionMetadata(
        destination: String,
    ): Job<MixinTransaction.Metadata<T>> = job {
        MixinTransaction.Metadata(
            destination,
            parseData()().merge(),
            provider.get()
        )
    }
}

public fun processClassForMixinContexts(
    mixinNode: ClassNode,
    targetNode: ClassNode,
    providers: MutableObjectContainer<MixinInjectionProvider<*, *>>,
    extension: ExtensionDescriptor,

    annotationProcessor: AnnotationProcessor,
): List<ProcessedMixinContext<*, *>> {
    return providers.objects().values.flatMap { provider ->
        annotationProcessor.process(
            mixinNode, provider.annotationType,
        ).map { element ->
            ProcessedMixinContext(
                provider as MixinInjectionProvider<Annotation, *>,
                MixinInjectionProvider.InjectionContext(
                    element,
                    targetNode,
                    extension,
                )
            )
        }
    }
}