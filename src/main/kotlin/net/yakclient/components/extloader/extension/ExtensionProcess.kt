package net.yakclient.components.extloader.extension

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archive.mapper.transform.mapClassName
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.boot.container.ContainerProcess
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.client.api.annotation.Mixin
import net.yakclient.common.util.immutableLateInit
import net.yakclient.components.extloader.api.environment.ApplicationMappingTarget
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.extloader.api.target.MixinTransaction
import net.yakclient.components.extloader.util.withDots
import net.yakclient.components.extloader.util.withSlashes
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.environment.mixinTypesAttrKey
import net.yakclient.components.extloader.api.extension.ExtensionVersionPartition
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.target.TargetLinker
import net.yakclient.components.extloader.util.parseNode
import org.objectweb.asm.tree.AnnotationNode
import java.lang.reflect.Proxy

public data class ExtensionProcess(
    val ref: ExtensionReference,
    private val context: ExtensionContext
) : ContainerProcess {
    override val archive: ExtensionArchiveHandle
        get() = ref.archive

    override fun start(): Unit = ref.extension.init()
}

public data class ExtensionReference(
    private val environment: ExtLoaderEnvironment,
    private val archiveReference: ExtensionArchiveReference,
    private val inheritanceTree: ClassInheritanceTree,
    private val getMappings: (ExtensionVersionPartition) -> ArchiveMapping,
    private val lazyLoader: (minecraft: TargetLinker) -> Pair<Extension, ExtensionArchiveHandle>,
) {
    public var extension: Extension by immutableLateInit()
    public var archive: ExtensionArchiveHandle by immutableLateInit()

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

    public fun injectMixins(register: (to: String, metadata: MixinTransaction.Metadata<*>) -> Unit) {
        archiveReference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .filterNot { it.name == "module-info.class" }
            .map { it to it.resource.open().parseNode() }
            .mapNotNull { (entry, node) ->
                (node.visibleAnnotations ?: listOf()).find { it.desc == "L${Mixin::class.java.name.withSlashes()};" }
                    ?.let { Triple(entry, node, instantiateAnnotation(it, Mixin::class.java)) }
            }.forEach { (entry, mixinNode, mixinAnnotation) ->
                val partition = archiveReference.reader.determinePartition(entry).first()  // Will always return 1
                if (partition !is ExtensionVersionPartition)
                    throw java.lang.IllegalArgumentException("Found mixin: '${mixinNode.name}' in partition: '${partition.name}' (path: '${partition.path}') which is not a version partition! Mixins can only exist in version partitions.")

                val providers = environment[mixinTypesAttrKey]!!.container.objects().values

                val mappings = getMappings(partition)

                val mappedTarget = mappings.mapClassName(
                    mixinAnnotation.value.withSlashes(),
                    partition.mappingNamespace,
                    environment[ApplicationMappingTarget]!!.namespace
                ) ?: mixinAnnotation.value.withSlashes()

                val targetNode = environment[ApplicationTarget]!!.reference.reference.reader[
                    "$mappedTarget.class"
                ]?.resource?.open()?.parseNode() ?: throw IllegalArgumentException(
                    "Failed to find target of mixin: '${mixinNode.name}' and injection: '${mixinAnnotation::class.java.name}'. " +
                            "Unmapped target (as compiled by extension: '${archiveReference.name}') was '${mixinAnnotation.value}', mapped target (what was searched for) is: '$mappedTarget'."
                )

                val methodInjections = mixinNode.methods.flatMap { methodNode ->
                    providers.flatMap { provider ->
                        (methodNode.visibleAnnotations ?: listOf()).filter { node ->
                            node.desc == "L${provider.annotationType.name.withSlashes()};"
                        }.map {
                            provider to it
                        }
                    }.map { (provider, annotationNode) ->
                        val annotation = instantiateAnnotation(annotationNode, provider.annotationType)

                        provider to MixinInjectionProvider.InjectionContext(
                            MixinInjectionProvider.InjectionElement(
                                annotation,
                                methodNode,
                                MixinInjectionProvider.InjectionElement.ElementType.METHOD,
                            ),
                            mixinNode,
                            targetNode
                        )
                    }
                }

                val fieldInjections = mixinNode.fields.flatMap { fieldNode ->
                    providers.flatMap { provider ->
                        (fieldNode.visibleAnnotations ?: listOf()).filter { node ->
                            node.desc == "L${provider.annotationType.name.withSlashes()};"
                        }.map {
                            provider to it
                        }
                    }.map { (provider, annotationNode) ->
                        val annotation = instantiateAnnotation(annotationNode, provider.annotationType)

                        provider to MixinInjectionProvider.InjectionContext(
                            MixinInjectionProvider.InjectionElement(
                                annotation,
                                fieldNode,
                                MixinInjectionProvider.InjectionElement.ElementType.METHOD,
                            ),
                            mixinNode,
                            targetNode
                        )
                    }
                }

                val classInjections = providers.flatMap { provider ->
                    (mixinNode.visibleAnnotations ?: listOf()).filter { node ->
                        node.desc == "L${provider.annotationType.name.withSlashes()};"
                    }.map {
                        provider to it
                    }
                }.map { (provider, annotationNode) ->
                    val annotation = instantiateAnnotation(annotationNode, provider.annotationType)

                    provider to MixinInjectionProvider.InjectionContext(
                        MixinInjectionProvider.InjectionElement(
                            annotation,
                            mixinNode,
                            MixinInjectionProvider.InjectionElement.ElementType.METHOD,
                        ),
                        mixinNode,
                        targetNode
                    )
                }

                (methodInjections + fieldInjections + classInjections).forEach { (provider, context) ->
                    register(
                        mappedTarget.withDots(), MixinTransaction.Metadata(
                            (provider as MixinInjectionProvider<Annotation, MixinInjection.InjectionData>).parseData(
                                context,
                                MixinInjectionProvider.MappingContext(
                                    inheritanceTree,
                                    mappings,
                                    partition.mappingNamespace,
                                    environment
                                ),
                                archiveReference
                            ), provider.get()
                        )
                    )
                }
            }
    }

    public fun setup(handle: TargetLinker) {
        val (e, a) = lazyLoader(handle)
        extension = e
        archive = a
    }
}