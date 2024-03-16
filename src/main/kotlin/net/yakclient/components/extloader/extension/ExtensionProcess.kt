package net.yakclient.components.extloader.extension

import com.durganmcbroom.jobs.*
import com.durganmcbroom.resources.openStream
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archive.mapper.transform.mapClassName
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.client.api.Extension
import net.yakclient.client.api.annotation.Mixin
import net.yakclient.common.util.immutableLateInit
import net.yakclient.components.extloader.api.environment.ApplicationMappingTarget
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.extloader.api.target.MixinTransaction
import net.yakclient.components.extloader.util.withDots
import net.yakclient.components.extloader.util.withSlashes
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.environment.extract
import net.yakclient.components.extloader.api.environment.mixinTypesAttrKey
import net.yakclient.components.extloader.api.extension.ExtensionVersionPartition
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.mixin.MixinException
import net.yakclient.components.extloader.target.TargetLinker
import net.yakclient.components.extloader.util.parseNode
import net.yakclient.`object`.MutableObjectContainer
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.lang.reflect.Proxy

public data class ExtensionContainer(
    private val environment: ExtLoaderEnvironment,
    private val archiveReference: ExtensionArchiveReference,
    private val inheritanceTree: ClassInheritanceTree,
    private val getMappings: (ExtensionVersionPartition) -> ArchiveMapping,
    private val lazyLoader: (TargetLinker) -> Pair<Extension, ExtensionArchiveHandle>,
) {
    public var extension: Extension by immutableLateInit()
    public var archive: ExtensionArchiveHandle by immutableLateInit()


    public fun injectMixins(register: (to: String, metadata: MixinTransaction.Metadata<*>) -> Unit): Job<Unit> =
        job(JobName("Inject mixins from extension: '${archiveReference.name}'")) {
            val iterableEntries = archiveReference.reader.entries()
                .filter { it.name.endsWith(".class") }
                .filterNot { it.name == "module-info.class" }

            for (entry in iterableEntries) {
                val mixinNode = entry.resource.openStream()
                    .parseNode()

                val mixinAnnotation = (mixinNode.visibleAnnotations
                    ?: listOf()).find { it.desc == "L${Mixin::class.java.name.withSlashes()};" }
                    ?.let { instantiateAnnotation(it, Mixin::class.java) } ?: continue

                val partition = archiveReference.reader.determinePartition(entry).first()  // Will always return 1
                if (partition !is ExtensionVersionPartition)
                    throw java.lang.IllegalArgumentException("Found mixin: '${mixinNode.name}' in partition: '${partition.name}' (path: '${partition.path}') which is not a version partition! Mixins can only exist in version partitions.")

                val providers = environment[mixinTypesAttrKey].extract().container

                val mappings = getMappings(partition)

                val mappedTarget = mappings.mapClassName(
                    mixinAnnotation.value.withSlashes(),
                    partition.mappingNamespace,
                    environment[ApplicationMappingTarget].extract().namespace
                ) ?: mixinAnnotation.value.withSlashes()

                val targetNode = environment[ApplicationTarget].extract().reference.reference.reader[
                    "$mappedTarget.class"
                ]?.resource?.openStream()
                    ?.parseNode() ?: throw IllegalArgumentException(
                    "Failed to find target of mixin: '${mixinNode.name}' and injection: '${mixinAnnotation::class.java.name}'. " +
                            "Unmapped target (as compiled by extension: '${archiveReference.name}') was '${mixinAnnotation.value}', mapped target (what was searched for) is: '$mappedTarget'."
                )

                val mixinContexts = processClassForMixinContexts(
                    mixinNode,
                    targetNode,
                    providers
                )

                mixinContexts.forEach { context ->
                    register(
                        mappedTarget.withDots(),
                        context.createTransactionMetadata(
                            MixinInjectionProvider.MappingContext(
                                inheritanceTree,
                                mappings,
                                partition.mappingNamespace,
                                environment
                            ),
                            archiveReference
                        )().merge()
                    )
                }
            }
        }.mapException { MixinException(it) }

    public fun setup(handle: TargetLinker) {
        val (e, a) = lazyLoader(handle)
        extension = e
        archive = a
    }
}

private fun <T : Annotation> instantiateAnnotation(annotationNode: AnnotationNode, annotationClass: Class<T>): T {
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
        else valuesMap[method.name]
    } as T

    return annotationInstance
}

public data class ProcessedMixinContext<A : Annotation, T : MixinInjection.InjectionData>(
    val provider: MixinInjectionProvider<A, T>,
    val context: MixinInjectionProvider.InjectionContext<A>
) {
    private fun parseData(
        mappingContext: MixinInjectionProvider.MappingContext,
        reference: ExtensionArchiveReference
    ): Job<T> = provider.parseData(
        context,
        mappingContext,
        reference
    )

    public fun createTransactionMetadata(
        mappingContext: MixinInjectionProvider.MappingContext,
        reference: ExtensionArchiveReference
    ): Job<MixinTransaction.Metadata<T>> = job {
        MixinTransaction.Metadata(
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