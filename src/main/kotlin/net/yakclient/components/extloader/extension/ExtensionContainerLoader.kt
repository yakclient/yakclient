package net.yakclient.components.extloader.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import net.yakclient.archive.mapper.*
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.*
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.loader.*
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionClassLoaderProvider
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.descriptor
import net.yakclient.components.extloader.api.extension.partition.*
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.versioning.ExtensionArchiveHandle
import net.yakclient.components.extloader.util.slice
import java.nio.file.Path

public open class ExtensionContainerLoader(
    private val parentClassloader: ClassLoader,
    private val environment: ExtLoaderEnvironment
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ExtensionContainerLoader

    public companion object : EnvironmentAttributeKey<ExtensionContainerLoader>

    private fun loadPartitions(
        environment: ExtLoaderEnvironment,
        ref: ArchiveReference,
        erm: ExtensionRuntimeModel,
        parents: List<ExtensionNode>,
        parentClassloader: ClassLoader,
    ): Job<List<ExtensionPartitionContainer<*, *>>> = job {
        val loaded = HashMap<ExtensionPartition, ExtensionPartitionContainer<*, *>>()

        val toLoad = erm.partitions.toMutableList()

        val allArchives = HashMap<String, ArchiveReference>()
        val allMetadata = HashMap<ExtensionPartition, ExtensionPartitionMetadata>()

        fun getArchive(path: String): ArchiveReference =
            allArchives[path] ?: run {
                ref.slice(Path.of(path)).also { allArchives[path] = it }
            }

        fun getMetadata(partition: ExtensionPartition): Job<ExtensionPartitionMetadata> = job {
            allMetadata[partition] ?: run {
                val loaders = environment[partitionLoadersAttrKey].extract().container
                val loader = loaders.get(partition.type) ?: throw IllegalArgumentException(
                    "Illegal partition type: '${partition.type}', only accepted ones are: '${
                        loaders.objects().map(
                            Map.Entry<String, ExtensionPartitionLoader<*>>::key
                        )
                    }'"
                )

                loader.parseMetadata(
                    partition,
                    getArchive(partition.path),
                    object : PartitionMetadataHelper {
                        override val runtimeModel: ExtensionRuntimeModel = erm
                        override val environment: ExtLoaderEnvironment = environment
                    }
                )().merge().also { allMetadata[partition] = it }
            }
        }

        fun loadPartition(
            partitionToLoad: ExtensionPartition,
            trace: List<String>,
        ): Job<ExtensionPartitionContainer<*, *>> = loaded[partitionToLoad]?.let { SuccessfulJob { it } } ?: job {
            val dependencyProviders: DependencyTypeContainer = environment[dependencyTypesAttrKey].extract().container
            val loaders = environment[partitionLoadersAttrKey].extract().container
            val archiveGraph = environment.archiveGraph

            check(trace.distinct().size == trace.size) { "Cyclic Partitions: '$trace' in extension: '${erm.name}'" }

            val loader = (loaders.get(partitionToLoad.type) as? ExtensionPartitionLoader<ExtensionPartitionMetadata>)
                ?: throw IllegalArgumentException(
                    "Illegal partition type: '${partitionToLoad.type}', only accepted ones are: '${
                        loaders.objects().map(
                            Map.Entry<String, ExtensionPartitionLoader<*>>::key
                        )
                    }'"
                )

            val metadata = getMetadata(partitionToLoad)().merge()

            loader.load(
                metadata,
                getArchive(partitionToLoad.path),
                object : PartitionLoaderHelper {
                    override val environment: ExtLoaderEnvironment = environment
                    override val runtimeModel: ExtensionRuntimeModel = erm
                    override val parents: List<ExtensionNode> = parents
                    override val parentClassloader: ClassLoader = parentClassloader
                    override val thisDescriptor: ArtifactMetadata.Descriptor =
                        ExtensionDescriptor(erm.groupId, erm.name, erm.version, partitionToLoad.name)

                    override val partitions: Map<ExtensionPartition, ExtensionPartitionMetadata>
                        get() = allMetadata

                    override fun addPartition(partition: ExtensionPartition): ExtensionPartitionMetadata {
                        toLoad.add(partition)

                        return getMetadata(partition)().merge()
                    }

                    override fun load(partition: ExtensionPartition): ExtensionPartitionContainer<*, *> {
                        return loadPartition(partition, trace + partition.name)().merge()
                    }

                    override fun access(scope: PartitionAccessTreeScope.() -> Unit): ArchiveAccessTree {
                        val directTargets = ArrayList<ArchiveTarget>()
                        val transitiveTargets = ArrayList<ArchiveTarget>()

                        val scopeObject = object : PartitionAccessTreeScope {
                            override fun withDefaults() {
                                val dependencies = dependenciesFromPartition(
                                    partitionToLoad,
                                    erm.name,
                                    dependencyProviders,
                                    archiveGraph
                                )().merge()

                                allDirect(dependencies)
                            }

                            override fun direct(node: ExtensionPartitionContainer<*, *>) {
                                rawTarget(
                                    ArchiveTarget(
                                        node.descriptor,
                                        object : ArchiveRelationship {
                                            override val name: String = "Lazy"
                                            override val classes: ClassProvider by lazy {
                                                ArchiveClassProvider(node.node.archive)
                                            }

                                            override val resources: ResourceProvider by lazy {
                                                ArchiveResourceProvider(node.node.archive)
                                            }
                                        }
                                    )
                                )
                            }

                            override fun direct(dependency: ArchiveNode<*>) {
                                directTargets.add(
                                    ArchiveTarget(
                                        dependency.descriptor,
                                        ArchiveRelationship.Direct(
                                            ArchiveClassProvider(dependency.archive),
                                            ArchiveResourceProvider(dependency.archive),
                                        )
                                    )
                                )

                                transitiveTargets.addAll(dependency.access.targets.map {
                                    ArchiveTarget(
                                        it.descriptor,
                                        ArchiveRelationship.Transitive(
                                            it.relationship.classes,
                                            it.relationship.resources,
                                        )
                                    )
                                })
                            }

                            override fun rawTarget(target: ArchiveTarget) {
                                directTargets.add(target)
                            }
                        }
                        scopeObject.scope()

                        return object : ArchiveAccessTree {
                            override val descriptor: ArtifactMetadata.Descriptor =
                                ExtensionDescriptor(erm.groupId, erm.name, erm.version, partitionToLoad.name)
                            override val targets: List<ArchiveTarget> = directTargets + transitiveTargets
                        }
                    }
                }
            )().merge().also { loaded[partitionToLoad] = it }
        }


        var i = 0
        while (i < toLoad.size) {
            val partition = toLoad[i++]

            if (loaded.contains(partition)) continue
            loadPartition(partition, listOf(partition.name))().merge()
        }

        loaded.values.toList()
    }

    public fun load(
        ref: ArchiveReference,
        parents: List<ExtensionNode>,
        erm: ExtensionRuntimeModel,
    ): Job<Pair<ExtensionContainer, List<ExtensionPartitionContainer<*, *>>>> = job {
        val loader = environment[ExtensionClassLoaderProvider].extract().createFor(
            ref,
            erm,
            listOf(),
            parentClassloader,
        )

        val partitions = loadPartitions(
            environment,
            ref,
            erm,
            parents,
            loader
        )().merge()

        ExtensionContainer(
            erm,
            environment,
            ref,
            partitions
        ) { linker ->
            val handle = ExtensionArchiveHandle(
                loader,
                erm.name,
                parents.mapNotNullTo(HashSet()) { it.archive },
                ArchiveSourceProvider(ref).packages
            )

            loader.partitions.addAll(partitions)

            handle
        } to partitions
    }.mapException {
        ExtensionLoadException(erm.descriptor, it) {
            erm.descriptor asContext "Extension name"
        }
    }
}