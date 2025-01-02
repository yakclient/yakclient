package dev.extframework.extension.core.minecraft.partition

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.findShortest
import dev.extframework.archive.mapper.newMappingsGraph
import dev.extframework.archive.mapper.transform.ClassInheritancePath
import dev.extframework.archive.mapper.transform.ClassInheritanceTree
import dev.extframework.archive.mapper.transform.mapClassName
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.ArchiveTree
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.archives.transform.TransformerConfig.Companion.plus
import dev.extframework.boot.archive.ArchiveRelationship
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.ArchiveClassProvider
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.ClassProvider
import dev.extframework.boot.loader.DelegatingClassProvider
import dev.extframework.common.util.LazyMap
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.delegate.Delegation
import dev.extframework.extension.core.feature.FeatureReference
import dev.extframework.extension.core.feature.findImplementedFeatures
import dev.extframework.extension.core.feature.implementsFeatures
import dev.extframework.extension.core.minecraft.environment.MappingNamespace
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.mappingTargetAttrKey
import dev.extframework.extension.core.minecraft.environment.remappersAttrKey
import dev.extframework.extension.core.minecraft.remap.ExtensionRemapper
import dev.extframework.extension.core.partition.TargetPartitionLoader
import dev.extframework.extension.core.partition.TargetPartitionMetadata
import dev.extframework.extension.core.partition.TargetPartitionNode
import dev.extframework.extension.core.target.TargetDescriptor
import dev.extframework.extension.core.util.parseNode
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.descriptor
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.partitionNamed
import dev.extframework.tooling.api.target.ApplicationTarget
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

public data class MinecraftPartitionMetadata(
    override val name: String,
    override val implementedFeatures: List<Pair<FeatureReference, String>>,
    override val archive: ArchiveReference?,

    override val enabled: Boolean,
    internal val supportedVersions: List<String>,
    internal val mappingNamespace: MappingNamespace,
) : TargetPartitionMetadata

public class MinecraftPartitionLoader(environment: ExtensionEnvironment) :
    TargetPartitionLoader<MinecraftPartitionMetadata>(environment) {
    private var appInheritanceTree: ClassInheritanceTree? = null

    private fun appInheritanceTree(
        app: ApplicationTarget
    ): ClassInheritanceTree {
        fun createPath(
            name: String
        ): ClassInheritancePath? {
            val stream =
                app.node.handle!!.classloader.getResourceAsStream(name.replace('.', '/') + ".class") ?: return null
            val node = ClassNode()
            ClassReader(stream).accept(node, 0)

            return ClassInheritancePath(
                node.name,
                node.superName?.let(::createPath),
                node.interfaces.mapNotNull { n ->
                    createPath(n)
                }
            )
        }

        if (appInheritanceTree == null) {
            appInheritanceTree = LazyMap(HashMap()) {
                createPath(it)
            }
        }

        return appInheritanceTree!!
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper
    ): Job<MinecraftPartitionMetadata> = job {
        val processor = environment[AnnotationProcessor].extract()
        val delegation = environment[Delegation].extract()

        val srcNS = partition.options["mappingNS"]
            ?.let(MappingNamespace::parse)
            ?: environment[mappingTargetAttrKey].extract().value
//            ?: throw IllegalArgumentException("No mappings type (property name: 'mappingNS') specified in the partition: '${partition.name}' in ext: '${helper.erm.descriptor}}.")
        val supportedVersions = partition.options["versions"]?.split(",")
            ?: throw IllegalArgumentException("Partition: '${partition.name}' in extension: '${helper.erm.descriptor} does not support any versions!")

        val implementedFeatures = reference?.let {
            it.reader.entries()
                .filter { it.name.endsWith(".class") }
                .map {
                    it.open().parseNode()
                }.filter {
                    it.implementsFeatures(processor)
                }.flatMap {
                    it.findImplementedFeatures(partition.name, processor, delegation)
                }.toList()
        } ?: listOf()

        val enabled = supportedVersions.contains(
            environment[ApplicationTarget].get().getOrNull()!!.node.descriptor.version
        )

        MinecraftPartitionMetadata(
            partition.name,
            implementedFeatures,
            reference,
            enabled,
            supportedVersions,
            srcNS,
        )
    }

    override fun load(
        metadata: MinecraftPartitionMetadata,
        //  /-- Want to instead use the archive defined in metadata for consistency
        unused: ArchiveReference?,
        // --\
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, MinecraftPartitionMetadata>> = job {
        val thisDescriptor = helper.erm.descriptor.partitionNamed(metadata.name)
        val reference = metadata.archive

        ExtensionPartitionContainer(
            thisDescriptor,
            metadata,
            if (metadata.enabled) {
                val appTarget = environment[ApplicationTarget].extract()

                val targetNS = environment[mappingTargetAttrKey].extract().value
                val mappings = newMappingsGraph(environment[mappingProvidersAttrKey].extract())
                    .findShortest(metadata.mappingNamespace.identifier, targetNS.identifier)
                    .forIdentifier(appTarget.node.descriptor.version)

                val (dependencies, target) = accessTree.targets
                    .map { it.relationship.node }
                    .filterIsInstance<ClassLoadedArchiveNode<*>>()
                    .partition { it.descriptor != TargetDescriptor }

                val handle = reference?.let {
                    remap(
                        reference,
                        mappings,
                        environment[remappersAttrKey]
                            .extract()
                            .sortedBy { it.priority },
                        metadata.mappingNamespace,
                        targetNS,
                        appInheritanceTree(appTarget),
                        accessTree.targets.asSequence()
                            .map(ArchiveTarget::relationship)
                            .map(ArchiveRelationship::node)
                            .filterIsInstance<ClassLoadedArchiveNode<*>>()
                            .mapNotNullTo(ArrayList(), ClassLoadedArchiveNode<*>::handle)
                    )().merge()

                    val sourceProviderDelegate = ArchiveSourceProvider(reference)
                    val cl = PartitionClassLoader(
                        thisDescriptor,
                        accessTree,
                        reference,
                        helper.parentClassLoader,
                        sourceProvider = sourceProviderDelegate, classProvider = object : ClassProvider {
                            val dependencyDelegate = DelegatingClassProvider(
                                dependencies
                                    .mapNotNull { it.handle }
                                    .map(::ArchiveClassProvider)
                            )
                            val targetDelegate = target.first().handle!!.classloader

                            override val packages: Set<String> = dependencyDelegate.packages + "*target*"

                            override fun findClass(name: String): Class<*>? {
                                return dev.extframework.common.util.runCatching(ClassNotFoundException::class) {
                                    targetDelegate.loadClass(
                                        name
                                    )
                                } ?: dependencyDelegate.findClass(name)
                            }
                        }
                    )

                    PartitionArchiveHandle(
                        "${helper.erm.name}-${metadata.name}",
                        cl,
                        reference,
                        setOf()
                    )
                }

                TargetPartitionNode(
                    handle,
                    accessTree,
                )
            } else {
                TargetPartitionNode(
                    null,
                    accessTree,
                )
            }
        )
    }


    private fun remap(
        reference: ArchiveReference,
        mappings: ArchiveMapping,
        remappers: List<ExtensionRemapper>,
        source: MappingNamespace,
        target: MappingNamespace,
        appTree: ClassInheritanceTree,
        dependencies: List<ArchiveTree>
    ) = job {
        fun remapTargetToSourcePath(
            path: ClassInheritancePath,
        ): ClassInheritancePath {
            return ClassInheritancePath(
                mappings.mapClassName(
                    path.name,
                    target.identifier,
                    source.identifier,
                ) ?: path.name,
                path.superClass?.let(::remapTargetToSourcePath),
                path.interfaces.map(::remapTargetToSourcePath)
            )
        }

        fun inheritancePathFor(
            node: ClassNode
        ): Job<ClassInheritancePath> = job {
            fun getParent(name: String?): ClassInheritancePath? {
                if (name == null) return null

                val treeFromApp = appTree[mappings.mapClassName(
                    name,
                    source.identifier,
                    target.identifier
                ) ?: name]?.let(::remapTargetToSourcePath)

                val treeFromRef = reference.reader["$name.class"]?.let { e ->
                    inheritancePathFor(
                        e.open().parseNode()
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

        val treeInternal = (reference.reader.entries())
            .filterNot(ArchiveReference.Entry::isDirectory)
            .filter { it.name.endsWith(".class") }
            .associate { e ->
                val path = inheritancePathFor(e.open().parseNode())().merge()
                path.name to path
            }

        val tree = LazyMap { key: String ->
            treeInternal[key] ?: appTree[
                mappings.mapClassName(
                    key,
                    source.identifier,
                    target.identifier
                ) ?: key
            ]?.let(::remapTargetToSourcePath)
        }

        val config: TransformerConfig = remappers.fold(
            TransformerConfig.of { } as TransformerConfig
        ) { acc: TransformerConfig, it ->
            val config: TransformerConfig = it.remap(
                mappings,
                tree,
                source.identifier,
                target.identifier
            )

            config + acc
        }

        reference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .forEach { entry ->
//                // TODO, this will recompute frames, see if we need to do that or not.
//                Archives.resolve(
//                    ClassReader(entry.resource.openStream()),
//                    config,
//                )

                reference.writer.put(
                    entry.transform(
                        config, dependencies
                    )
                )
            }
    }
//
//    private fun <A : Annotation, T : MixinInjection.InjectionData> ProcessedMixinContext<A, T>.createMappedTransactionMetadata(
//        destination: String,
//        mappingContext: MappingInjectionProvider.Context
//    ): Job<MixinTransaction.Metadata<T>> = job {
//        val provider = provider as? MappingInjectionProvider<A, T> ?: throw MixinException(
//            message = "Illegal mixin injection provider: '${provider.type}'. Expected it to be a subtype of '${MappingInjectionProvider::class.java.name}' however it was not."
//        ) {
//            solution("Wrap your provider in a mapping provider")
//            solution("Implement '${MappingInjectionProvider::class.java.name}'")
//        }
//
//        MixinTransaction.Metadata(
//            destination,
//            provider.mapData(
//                provider.parseData(this@createMappedTransactionMetadata.context)().merge(),
//                mappingContext
//            )().merge(),
//            provider.get()
//        )
//    }


//    private fun mapperFor(
//        archive: ArchiveReference,
//        dependencies: List<ArchiveTree>,
//        mappings: ArchiveMapping,
//        tree: ClassInheritanceTree,
//        srcNS: String,
//        targetNS: String
//    ): TransformerConfig {
//        fun ClassInheritancePath.fromTreeInternal(): ClassInheritancePath {
//            val mappedName = mappings.mapClassName(name, srcNS, targetNS) ?: name
//
//            return ClassInheritancePath(
//                mappedName,
//                superClass?.fromTreeInternal(),
//                interfaces.map { it.fromTreeInternal() }
//            )
//        }
//
//        val lazilyMappedTree = LazyMap<String, ClassInheritancePath> {
//            tree[mappings.mapClassName(it, srcNS, targetNS)]?.fromTreeInternal()
//        }
//
//        return mappingTransformConfigFor(
//            ArchiveTransformerContext(
//                archive,
//                dependencies,
//                mappings,
//                srcNS,
//                targetNS,
//                lazilyMappedTree,
//            )
//        )
//    }

}