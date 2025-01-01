package dev.extframework.extension.core.mixin

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.mixin.MixinInjection
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.archives.transform.TransformerConfig.Companion.plus
import dev.extframework.core.api.mixin.Mixin
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.environment.mixinTypesAttrKey
import dev.extframework.extension.core.partition.TargetPartitionMetadata
import dev.extframework.extension.core.util.*
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.tooling.api.target.ApplicationTarget
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

public class DefaultMixinSubsystem(
    private val environment: ExtensionEnvironment
) : MixinSubsystem {
    private val injections: MutableMap<String, MutableList<MixinTransaction.Metadata<*>>> = HashMap()

    override fun process(ctx: MixinProcessContext): Job<Boolean> = job {
        val (node) = ctx

        val parsedInjections = node.partitions
            .asSequence()
            .map(ExtensionPartitionContainer<*, *>::metadata)
            .filterIsInstance<TargetPartitionMetadata>()
            .filter(TargetPartitionMetadata::enabled)
            .mapNotNull(TargetPartitionMetadata::archive)
            .map(ArchiveReference::reader)
            .flatMap(ArchiveReference.Reader::entries)
            .filter { it.name.endsWith(".class") }
            .mapNotNull { entry ->
                val mixinNode = entry.open()
                    .parseNode()

                val mixinAnno =
                    ((mixinNode.visibleAnnotations ?: listOf())
                        .find { it.desc == Type.getType(Mixin::class.java).descriptor }
                        ?: return@mapNotNull null).createValueMap()

                val mixinDest = (mixinAnno["value"] as Type).className
                val subsystem = mixinAnno["subsystem"] as? Type
                // If the subsystem is null or Any/Object, then we want the default. (obviously if its this type then we also want the default)
                if (subsystem != Type.getType(this::class.java) && subsystem != Type.getType(Any::class.java) && subsystem != null) {
                    return@mapNotNull null
                }

                val providers = environment[mixinTypesAttrKey].extract().container

                val targetNode = environment[ApplicationTarget]
                    .extract()
                    .node
                    .handle
                    ?.classloader
                    ?.getResourceAsStream("${mixinDest.withSlashes()}.class")
                    ?.parseNode() ?: throw IllegalArgumentException(
                    "Failed to find target of mixin: '${mixinNode.name.withDots()}' and injection: '${Mixin::class.java.name}'. " +
                            "Target (as compiled by extension: '${node.descriptor}') was '${mixinDest}'."
                )

                processClassForMixinContexts(
                    mixinNode,
                    targetNode,
                    providers,
                    node.descriptor,
                    environment[AnnotationProcessor].extract()
                )
            }
            .flatMap { it: List<ProcessedMixinContext<*, *>> -> it }
            .map { it.createTransactionMetadata(it.context.targetNode.name.withDots())().merge() }
            .groupBy { it.destination }

        parsedInjections.forEach { (dst, ctx) ->
            (injections[dst] ?: run {
                val list = ArrayList<MixinTransaction.Metadata<*>>()
                injections[dst] = list
                list
            }).addAll(ctx)
        }

        parsedInjections.isNotEmpty()
    }

    override fun transformClass(name: String, node: ClassNode?): ClassNode? {
        var node = node ?: return null

        val config = injections[name]?.fold(TransformerConfig.of { }) { acc, it ->
            it as MixinTransaction.Metadata<MixinInjection.InjectionData>

            it.injection.apply(it.data) + acc
        } ?: return node

        node = config.ct(node)
        node.methods = node.methods.map {
            config.mt.invoke(it)
        }
        node.fields = node.fields.map {
            config.ft.invoke(it)
        }

        return node
    }
}