package dev.extframework.components.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.openStream
import dev.extframework.archive.mapper.*
import dev.extframework.archive.mapper.transform.*
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.ArchiveTree
import dev.extframework.archives.Archives
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.common.util.LazyMap
import dev.extframework.components.extloader.api.environment.*
import dev.extframework.components.extloader.api.extension.ExtensionPartition
import dev.extframework.components.extloader.api.extension.descriptor
import dev.extframework.components.extloader.api.extension.partition.*
import dev.extframework.components.extloader.api.mixin.MixinInjectionProvider
import dev.extframework.components.extloader.api.target.ApplicationTarget
import dev.extframework.components.extloader.api.target.MixinTransaction
import dev.extframework.components.extloader.extension.artifact.ExtensionDescriptor
import dev.extframework.components.extloader.extension.feature.FeatureReference
import dev.extframework.components.extloader.extension.feature.containsFeatures
import dev.extframework.components.extloader.extension.feature.findFeatures
import dev.extframework.components.extloader.extension.processClassForMixinContexts
import dev.extframework.components.extloader.util.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

public data class VersionedPartitionMetadata(
    override val name: String,
    public val implementedFeatures: List<FeatureReference>,
    public val mixins: Sequence<MixinTransaction.Metadata<*>>,
    public val enabled: Boolean,
    val archive: ArchiveReference,
    val mappingNamespace: String,
    public val supportedVersions: List<String>,
) : ExtensionPartitionMetadata

public open class VersionedPartitionNode internal constructor(
    override val archive: ArchiveHandle,
    override val access: ArchiveAccessTree,
) : ExtensionPartitionNode {
    internal constructor(descriptor: ArtifactMetadata.Descriptor) : this(
        emptyArchiveHandle(),
        object : ArchiveAccessTree {
            override val descriptor: ArtifactMetadata.Descriptor = descriptor
            override val targets: List<ArchiveTarget> = listOf()
        }
    )
}

public class VersionedPartitionLoader : ExtensionPartitionLoader<VersionedPartitionMetadata> {
    override val type: String = TYPE
    private var appInheritanceTree: ClassInheritanceTree? = null
    public companion object {
        public const val TYPE: String = "versioned"
    }

    override fun parseMetadata(
        partition: ExtensionPartition,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper
    ): Job<VersionedPartitionMetadata> = job {
        val target = helper.environment[ApplicationTarget].extract()

        val srcNS = partition.options["mappingNS"]
            ?: throw IllegalArgumentException("No mappings type (property name: 'mappingNS') specified in the partition: '${partition.name}' in ext: '${helper.runtimeModel.descriptor}}.")
        val supportedVersions = partition.options["versions"]?.split(",")
            ?: throw IllegalArgumentException("Partition: '${partition.name}' in extension: '${helper.runtimeModel.descriptor} does not support any versions!")

        val implementedFeatures = reference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .map {
                it.resource.openStream().parseNode()
            }.filter {
                it.containsFeatures()
            }.flatMap {
                it.findFeatures()
            }.toList()

        if (appInheritanceTree == null) {
            appInheritanceTree = createFakeInheritanceTree(target.reference.reference.reader)
        }

        val targetNS = helper.environment[ApplicationMappingTarget].extract().namespace
        val mappings = newMappingsGraph(helper.environment[mappingProvidersAttrKey].extract())
            .findShortest(srcNS, targetNS)
            .forIdentifier(target.reference.descriptor.version)

        val remappedTree = remap(
            reference,
            // TODO this isnt right
            listOf(),
            mappings,
            srcNS,
            targetNS,
            appInheritanceTree!!
        )().merge()

        val mixins: Sequence<MixinTransaction.Metadata<*>> = setupMixinData(
            helper.runtimeModel.descriptor,
            reference,
            helper.environment,
            mappings,
            srcNS,
            remappedTree
        )().merge()

        val enabled = supportedVersions.contains(
            helper.environment[ApplicationTarget].get().getOrNull()!!.reference.descriptor.version
        )

        VersionedPartitionMetadata(
            partition.name,
            implementedFeatures,
            mixins,
            enabled,
            reference,
            srcNS,
            supportedVersions
        )
    }


    override fun load(
        metadata: VersionedPartitionMetadata,
        reference: ArchiveReference,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, *>> =
        job(JobName("Load partition container: ${metadata.name}")) {
            ExtensionPartitionContainer<VersionedPartitionNode, VersionedPartitionMetadata>(
                helper.thisDescriptor,
                metadata
            ) { linker ->
                if (!metadata.enabled) return@ExtensionPartitionContainer VersionedPartitionNode(helper.thisDescriptor)

                val access = helper.access {
                    withDefaults()

                    direct(
                        helper.load(
                            helper.partitions
                                .keys
                                .find { it.type == MainPartitionLoader.TYPE }
                                ?: noMainPartition(metadata, helper)
                        ).also {
                            (it as TargetRequiringPartitionContainer<*, *>).setup(linker)().merge()
                        }
                    )

                    direct(
                        helper.load(
                            helper.partitions
                                .keys
                                .first { it.type == FeaturePartitionLoader.TYPE }
                        ).also {
                            (it as TargetRequiringPartitionContainer<*, *>).setup(linker)().merge()
                        }
                    )

                    rawTarget(linker.targetTarget)

                    helper.parents.mapNotNull { node ->
                        node.container?.partitions
                            ?.filter { it.metadata is MainPartitionMetadata }
                            ?.map { it.node }
                            ?.first { it is MainPartitionNode }
                            ?.let { it to node }
                    }.forEach { (it, node) ->
                        rawTarget(it.directTarget(node.descriptor))
                    }
                }

                val sourceProviderDelegate = ArchiveSourceProvider(reference)
                val cl = PartitionClassLoader(
                    helper.runtimeModel,
                    "${helper.runtimeModel.name}-${metadata.name}",
                    access,
                    reference,
                    helper.parentClassloader,

                    sourceProvider = sourceProviderDelegate,
//                    sourceProvider = object : SourceProvider by sourceProviderDelegate {
//                        private val featureContainers = metadata.implementedFeatures.mapTo(HashSet()) { it.container.withDots() }
//
//                        override fun findSource(name: String): ByteBuffer? {
//                            return if (featureContainers.contains(name)) null
//                            else sourceProviderDelegate.findSource(name)
//                        }
//                    }
                )

                val handle = PartitionArchiveHandle(
                    "${helper.runtimeModel.name}-${metadata.name}",
                    helper.runtimeModel,
                    cl,
                    reference,
                    setOf()
                )

                VersionedPartitionNode(
                    handle,
                    access,
                )
            }
        }

    private fun setupMixinData(
        extension: ExtensionDescriptor,
        archiveReference: ArchiveReference,
        environment: ExtLoaderEnvironment,
        mappings: ArchiveMapping,
        mappingNamespace: String,
        inheritanceTree: ClassInheritanceTree,
    ): Job<Sequence<MixinTransaction.Metadata<*>>> = job(JobName("Parse mixin transactions")) {
        archiveReference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .mapNotNull { entry ->
                val mixinNode = entry.resource.openStream()
                    .parseNode()

                val mixinValues = ((mixinNode.visibleAnnotations
                    ?: listOf()).find { it.desc == "L${dev.extframework.client.api.annotation.Mixin::class.java.name.withSlashes()};" } ?: return@mapNotNull null)
                    .values.withIndex().groupBy {
                        it.index / 2
                    }.values.associate { it[0].value as String to it[1].value }

                val mixinRawDest = (mixinValues["value"] as Type).internalName

                val providers = environment[mixinTypesAttrKey].extract().container

                val mappedTarget = mappings.mapClassName(
                    mixinRawDest.withSlashes(),
                    mappingNamespace,
                    environment[ApplicationMappingTarget].extract().namespace
                ) ?: mixinRawDest.withSlashes()

                val targetNode = environment[ApplicationTarget].extract().reference.reference.reader[
                    "$mappedTarget.class"
                ]?.resource?.openStream()
                    ?.parseNode() ?: throw IllegalArgumentException(
                    "Failed to find target of mixin: '${mixinNode.name}' and injection: '${dev.extframework.client.api.annotation.Mixin::class.java.name}'. " +
                            "Unmapped target (as compiled by extension: '$extension') was '${mixinRawDest.withDots()}', mapped target (what was searched for) is: '$mappedTarget'."
                )

                processClassForMixinContexts(
                    mixinNode,
                    targetNode,
                    providers
                ).map {
                    it.createTransactionMetadata(
                        mixinRawDest.withDots(),
                        MixinInjectionProvider.MappingContext(
                            inheritanceTree,
                            mappings,
                            mappingNamespace,
                            environment,
                            extension.artifact
                        ),
                        archiveReference
                    )().merge()
                }
            }.flatMap { it }
    }

    private fun remap(
        archiveReference: ArchiveReference,
        // Dependencies should already be mapped
        dependencies: List<ArchiveTree>,
        mappings: ArchiveMapping,
        srcNS: String,
        targetNS: String,

        appInheritanceTree: ClassInheritanceTree,
    ): Job<ClassInheritanceTree> = job(JobName("Remap extension")) {
        // Gets all the loaded mixins and map them to their actual location in the archive reference.

        fun inheritancePathFor(
            node: ClassNode
        ): Job<ClassInheritancePath> = job {
            fun getParent(name: String?): ClassInheritancePath? {
                if (name == null) return null

                val treeFromApp = appInheritanceTree[mappings.mapClassName(
                    name,
                    srcNS,
                    targetNS
                ) ?: name]

                val treeFromRef = archiveReference.reader["$name.class"]?.let { e ->
                    inheritancePathFor(
                        e.resource.openStream().parseNode()
                    )().merge()
                }

                val treeFromDependencies = dependencies.firstNotNullOfOrNull {
                    it.getResource("$name.class")?.parseNode()?.let(::inheritancePathFor)?.invoke()?.merge()
                }

                return treeFromApp ?: treeFromRef ?: treeFromDependencies
            }

            ClassInheritancePath(
                node.name,
                getParent(node.superName),
                node.interfaces.mapNotNull { getParent(it) }
            )
        }

        val treeInternal = (archiveReference.reader.entries())
            .filterNot(ArchiveReference.Entry::isDirectory)
            .filter { it.name.endsWith(".class") }
            .associate { e ->
                val path = inheritancePathFor(
                    e.resource.openStream()
                        .parseNode()
                )().merge()
                path.name to path
            }

        val tree = object : Map<String, ClassInheritancePath> by treeInternal {
            override fun get(key: String): ClassInheritancePath? {
                return treeInternal[key] ?: appInheritanceTree[key]
            }
        }

        // Map each entry based on its respective mapper
        archiveReference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .forEach { entry ->
                val config = mapperFor(
                    archiveReference,
                    dependencies,
                    mappings,
                    tree,
                    srcNS,
                    targetNS
                )

                // TODO, this will recompute frames, see if we need to do that or not.
                Archives.resolve(
                    ClassReader(entry.resource.openStream()),
                    config,
                )

                archiveReference.writer.put(
                    entry.transform(
                        config, dependencies
                    )
                )
            }

        tree
    }

    private fun mapperFor(
        archive: ArchiveReference,
        dependencies: List<ArchiveTree>,
        mappings: ArchiveMapping,
        tree: ClassInheritanceTree,
        srcNS: String,
        targetNS: String
    ): TransformerConfig {
        fun ClassInheritancePath.fromTreeInternal(): ClassInheritancePath {
            val mappedName = mappings.mapClassName(name, srcNS, targetNS) ?: name

            return ClassInheritancePath(
                mappedName,
                superClass?.fromTreeInternal(),
                interfaces.map { it.fromTreeInternal() }
            )
        }

        val lazilyMappedTree = LazyMap<String, ClassInheritancePath> {
            tree[mappings.mapClassName(it, srcNS, targetNS)]?.fromTreeInternal()
        }

        return mappingTransformConfigFor(
            ArchiveTransformerContext(
                archive,
                dependencies,
                mappings,
                srcNS,
                targetNS,
                lazilyMappedTree,
            )
        )
    }
}