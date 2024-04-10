package net.yakclient.components.extloader.extension

import com.durganmcbroom.jobs.*
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.client.api.Extension
import net.yakclient.common.util.immutableLateInit
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionContainer
import net.yakclient.components.extloader.api.extension.partition.TargetRequiringPartitionContainer
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import net.yakclient.components.extloader.api.target.MixinTransaction
import net.yakclient.components.extloader.extension.partition.VersionedPartitionMetadata
import net.yakclient.components.extloader.mixin.MixinException
import net.yakclient.components.extloader.target.TargetLinker
import net.yakclient.components.extloader.util.instantiateAnnotation
import net.yakclient.components.extloader.util.withSlashes
import net.yakclient.`object`.MutableObjectContainer
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

public data class ExtensionContainer(
    private val environment: ExtLoaderEnvironment,
    private val extRef: ArchiveReference,
    public val partitions: List<ExtensionPartitionContainer<*, *>>,
    private val lazyLoader: JobScope.(TargetLinker) -> Pair<Extension, ExtensionArchiveHandle>,
) {
    public var extension: Extension by immutableLateInit()
    public var archive: ExtensionArchiveHandle by immutableLateInit()

    public fun injectMixins(register: (metadata: MixinTransaction.Metadata<*>) -> Unit): Job<Unit> =
        job(JobName("Inject mixins from extension: '${extRef.name}'")) {
            partitions.map { it.metadata }.filterIsInstance<VersionedPartitionMetadata>().flatMap {
                it.mixins
            }.forEach { context ->
                register(
                    context
                )
            }
        }.mapException { MixinException(it) }

    public fun setup(linker: TargetLinker) : Job<Unit> = job {
        partitions
            .filterIsInstance<TargetRequiringPartitionContainer<*, *>>()
            .forEach { it.setup(linker)().merge() }

        val (e, a) = lazyLoader(linker)
        extension = e
        archive = a
    }
}


public data class ProcessedMixinContext<A : Annotation, T : MixinInjection.InjectionData>(
    val provider: MixinInjectionProvider<A, T>,
    val context: MixinInjectionProvider.InjectionContext<A>
) {
    private fun parseData(
        mappingContext: MixinInjectionProvider.MappingContext,
        reference: ArchiveReference
    ): Job<T> = provider.parseData(
        context,
        mappingContext,
        reference
    )

    public fun createTransactionMetadata(
        destination: String,
        mappingContext: MixinInjectionProvider.MappingContext,
        reference: ArchiveReference
    ): Job<MixinTransaction.Metadata<T>> = job {
        MixinTransaction.Metadata(
            destination,
            parseData(mappingContext, reference)().merge(),
            provider.get()
        )
    }
}

public fun processClassForMixinContexts(
    mixinNode: ClassNode,
    targetNode: ClassNode,
    providers: MutableObjectContainer<MixinInjectionProvider<*, *>>
): List<ProcessedMixinContext<*, *>> {
    val mixinProviders = providers.objects().values

    fun <T : Annotation> createContext(
        annotation: T,
        node: Any,
        type: MixinInjectionProvider.InjectionElement.ElementType,
    ): MixinInjectionProvider.InjectionContext<T> {
        return MixinInjectionProvider.InjectionContext(
            MixinInjectionProvider.InjectionElement(
                annotation,
                node,
                type,
            ),
            mixinNode,
            targetNode
        )
    }

    fun <A : Annotation> createPair(
        node: Any,
        type: MixinInjectionProvider.InjectionElement.ElementType,
        provider: MixinInjectionProvider<A, *>,
        annotationNode: AnnotationNode
    ): ProcessedMixinContext<A, *> {
        val annotation = instantiateAnnotation(annotationNode, provider.annotationType)

        return ProcessedMixinContext(
            provider,
            createContext(annotation, node, type)
        )
    }

    fun createInjectionData(
        node: Any,
        type: MixinInjectionProvider.InjectionElement.ElementType,
        annotations: List<AnnotationNode>
    ): List<ProcessedMixinContext<*, *>> {
        return mixinProviders.flatMap { provider ->
            annotations.filter { node ->
                node.desc == "L${provider.annotationType.name.withSlashes()};"
            }.map {
                provider to it
            }
        }.map {
            createPair(node, type, it.first, it.second)
        }
    }

    val methodInjections = mixinNode.methods.flatMap { methodNode ->
        createInjectionData(
            methodNode,
            MixinInjectionProvider.InjectionElement.ElementType.METHOD,
            methodNode.visibleAnnotations ?: listOf()
        )
    }

    val fieldInjections = mixinNode.fields.flatMap { fieldNode ->
        createInjectionData(
            fieldNode,
            MixinInjectionProvider.InjectionElement.ElementType.FIELD,
            fieldNode.visibleAnnotations ?: listOf()
        )
    }

    val classInjections = createInjectionData(
        mixinNode,
        MixinInjectionProvider.InjectionElement.ElementType.CLASS,
        mixinNode.visibleAnnotations ?: listOf()
    )

    return methodInjections + fieldInjections + classInjections
}