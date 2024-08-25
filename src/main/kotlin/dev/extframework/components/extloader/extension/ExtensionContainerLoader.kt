//package dev.extframework.components.extloader.extension
//
//import com.durganmcbroom.artifact.resolver.ArtifactMetadata
//import com.durganmcbroom.jobs.Job
//import com.durganmcbroom.jobs.SuccessfulJob
//import com.durganmcbroom.jobs.job
//import com.durganmcbroom.jobs.mapException
//import dev.extframework.archive.mapper.*
//import dev.extframework.archive.mapper.transform.*
//import dev.extframework.archives.ArchiveReference
//import dev.extframework.boot.archive.*
//import dev.extframework.boot.dependency.DependencyTypeContainer
//import dev.extframework.boot.loader.*
//import dev.extframework.components.extloader.api.environment.*
//import dev.extframework.components.extloader.api.extension.ExtensionClassLoaderProvider
//import dev.extframework.components.extloader.api.extension.PartitionRuntimeModel
//import dev.extframework.components.extloader.api.extension.ExtensionRuntimeModel
//import dev.extframework.components.extloader.api.extension.descriptor
//import dev.extframework.components.extloader.api.extension.partition.*
//import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
//import dev.extframework.components.extloader.extension.partition.PartitionLoadException
//import dev.extframework.components.extloader.util.slice
//import java.nio.file.Path
//
//public open class ExtensionContainerLoader(
//    private val parentClassloader: ClassLoader,
//    private val environment: ExtensionEnvironment
//) : EnvironmentAttribute {
//    override val key: EnvironmentAttributeKey<*> = ExtensionContainerLoader
//
//    public companion object : EnvironmentAttributeKey<ExtensionContainerLoader>
//
//    private fun loadPartitions(
//        environment: ExtensionEnvironment,
//        ref: ArchiveReference,
//        erm: ExtensionRuntimeModel,
//        parents: List<ExtensionPartition>,
//        parentClassloader: ClassLoader,
//        accessTree: ArchiveAccessTree,
//    ): Job<List<ExtensionPartitionContainer<*, *>>> = job {
//        val loaded = HashMap<PartitionRuntimeModel, ExtensionPartitionContainer<*, *>>()
//
//        val toLoad = erm.partitions.toMutableList()
//
//        val allArchives = HashMap<String, ArchiveReference>()
//        val allMetadata = HashMap<PartitionRuntimeModel, ExtensionPartitionMetadata>()
//
//        fun getArchive(path: String): ArchiveReference =
//            allArchives[path] ?: run {
//                ref.slice(Path.of(path)).also { allArchives[path] = it }
//            }
//
//        fun getMetadata(partition: PartitionRuntimeModel): Job<ExtensionPartitionMetadata> = job {
//            allMetadata[partition] ?: run {
//                val loaders = environment[partitionLoadersAttrKey].extract().container
//                val loader = loaders.get(partition.type) ?: throw IllegalArgumentException(
//                    "Illegal partition type: '${partition.type}', only accepted ones are: '${
//                        loaders.objects().map(
//                            Map.Entry<String, ExtensionPartitionLoader<*>>::key
//                        )
//                    }'"
//                )
//
//                loader.parseMetadata(
//                    partition,
//                    getArchive(partition.path),
//                    object : PartitionMetadataHelper {
//                        override val runtimeModel: ExtensionRuntimeModel = erm
//                        override val environment: ExtensionEnvironment = environment
//                    }
//                )().merge().also { allMetadata[partition] = it }
//            }
//        }
//
//        fun loadPartition(
//            partitionToLoad: PartitionRuntimeModel,
//            trace: List<String>,
//        ): Job<ExtensionPartitionContainer<*, *>> = loaded[partitionToLoad]?.let { SuccessfulJob { it } } ?: job {
//            val dependencyProviders: DependencyTypeContainer = environment[dependencyTypesAttrKey].extract().container
//            val loaders = environment[partitionLoadersAttrKey].extract().container
//
//            check(trace.distinct().size == trace.size) { "Cyclic Partitions: '$trace' in extension: '${erm.name}'" }
//
//            val loader = (loaders.get(partitionToLoad.type) as? ExtensionPartitionLoader<ExtensionPartitionMetadata>)
//                ?: throw IllegalArgumentException(
//                    "Illegal partition type: '${partitionToLoad.type}', only accepted ones are: '${
//                        loaders.objects().map(
//                            Map.Entry<String, ExtensionPartitionLoader<*>>::key
//                        )
//                    }'"
//                )
//
//            val metadata = getMetadata(partitionToLoad)().merge()
//
//            loader.load(
//                metadata,
//                getArchive(partitionToLoad.path),
//                object : PartitionLoaderHelper {
//                    override val environment: ExtensionEnvironment = environment
//                    override val runtimeModel: ExtensionRuntimeModel = erm
//                    override val parents: List<ExtensionPartition> = parents
//                    override val parentClassLoader: ClassLoader = parentClassloader
//                    override val thisDescriptor: ArtifactMetadata.Descriptor =
//                        ExtensionDescriptor(erm.groupId, erm.name, erm.version, partitionToLoad.name)
//
//                    override val partitions: Map<PartitionRuntimeModel, ExtensionPartitionMetadata>
//                        get() = allMetadata
//
//                    override fun addPartition(partition: PartitionRuntimeModel): ExtensionPartitionMetadata {
//                        toLoad.add(partition)
//
//                        return getMetadata(partition)().merge()
//                    }
//
//                    override fun load(partition: PartitionRuntimeModel): ExtensionPartitionContainer<*, *> {
//                        return loadPartition(partition, trace + partition.name)().merge()
//                    }
//
//                    override fun access(scope: PartitionAccessTreeScope.() -> Unit): ArchiveAccessTree {
//                        val targets = ArrayList<ArchiveTarget>()
//                        val partitionTargets = ArrayList<ExtensionPartitionContainer<*, *>>()
//
//                        val scopeObject = object : PartitionAccessTreeScope {
//                            override fun withDefaults() {
//                                val dependencies = partitionToLoad.dependencies.map { dep ->
//                                    dependencyProviders.objects().values.firstNotNullOfOrNull {
//                                        it.parseRequest(dep)
//                                    } ?: throw PartitionLoadException(
//                                        partitionToLoad.name,
//                                        "Failed to parse dependency request.",
//                                    ) {
//                                        dep asContext "Raw dependency request"
//                                    }
//                                }.mapTo(HashSet()) { it.descriptor }
//
//                                accessTree.targets.filter {
//                                    dependencies.contains(it.descriptor)
//                                }.forEach {
//                                    direct(it.relationship.node)
//                                }
//                            }
//
//                            override fun direct(node: ExtensionPartitionContainer<*, *>) {
//                                partitionTargets.add(node)
//                            }
//
//                            override fun direct(dependency: ArchiveNode<*>) {
//                                rawTarget(
//                                    ArchiveTarget(
//                                        dependency.descriptor,
//                                        ArchiveRelationship.Direct(
//                                            dependency,
//                                        )
//                                    )
//                                )
//
//                                targets.addAll(dependency.access.targets.map {
//                                    ArchiveTarget(
//                                        it.descriptor,
//                                        ArchiveRelationship.Transitive(
//                                            it.relationship.node
//                                        )
//                                    )
//                                })
//                            }
//
//                            override fun rawTarget(target: ArchiveTarget) {
//                                targets.add(target)
//                            }
//                        }
//                        scopeObject.scope()
//
//                        return object : PartitionAccessTree {
//                            override val partitions: List<ExtensionPartitionContainer<*, *>> = partitionTargets
//                            override val descriptor: ArtifactMetadata.Descriptor =
//                                ExtensionDescriptor(
//                                    erm.groupId,
//                                    erm.name,
//                                    erm.version,
//                                    partitionToLoad.name
//                                )
//                            override val targets: List<ArchiveTarget> = targets
//                        }
//                    }
//                }
//            )().merge().also { loaded[partitionToLoad] = it }
//        }
//
//        var i = 0
//        while (i < toLoad.size) {
//            val partition = toLoad[i++]
//
//            if (loaded.contains(partition)) continue
//            loadPartition(partition, listOf(partition.name))().merge()
//        }
//
//        loaded.values.toList()
//    }
//
//    public fun load(
//        ref: ArchiveReference,
//        parents: List<ExtensionPartition>,
//        erm: ExtensionRuntimeModel,
//        accessTree: ArchiveAccessTree
//    ): Job<Pair<ExtensionContainer, List<ExtensionPartitionContainer<*, *>>>> = job {
//        val loader = environment[ExtensionClassLoaderProvider].extract().createFor(
//            ref,
//            erm,
//            listOf(),
//            parentClassloader,
//        )
//
//        val partitions = loadPartitions(
//            environment,
//            ref,
//            erm,
//            parents,
//            loader,
//            accessTree
//        )().merge()
//
//        ExtensionContainer(
//            erm,
//            environment,
//            ref,
//            partitions
//        ) { linker ->
//            val handle = DefaultExtensionArchiveHandle(
//                loader,
//                erm.name,
//                parents.mapNotNullTo(HashSet()) { it.handle },
//                ArchiveSourceProvider(ref).packages
//            )
//
//            loader.partitions.addAll(partitions)
//
//            handle
//        } to partitions
//    }.mapException {
//        ExtensionLoadException(erm.descriptor, it) {
//            erm.descriptor asContext "Extension name"
//        }
//    }
//}