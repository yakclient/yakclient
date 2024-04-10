package net.yakclient.components.extloader.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.job
import net.yakclient.archive.mapper.*
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.*
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.dependency.DependencyResolverProvider
import net.yakclient.boot.loader.*
import net.yakclient.boot.util.firstNotFailureOf
import net.yakclient.components.extloader.EXT_LOADER_ARTIFACT
import net.yakclient.components.extloader.EXT_LOADER_GROUP
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionClassLoaderProvider
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.descriptor
import net.yakclient.components.extloader.api.extension.partition.*
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.partition.MainPartitionMetadata
import net.yakclient.components.extloader.extension.partition.MainPartitionNode
import net.yakclient.components.extloader.extension.versioning.VersionedExtArchiveHandle
import net.yakclient.components.extloader.util.slice
import java.lang.IllegalStateException
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
        ): Job<ExtensionPartitionContainer<*, *>> = loaded[partitionToLoad]?.let { SuccessfulJob { it }} ?: job {
            val dependencyProviders = environment[dependencyTypesAttrKey].extract().container
            val loaders = environment[partitionLoadersAttrKey].extract().container
            val archiveGraph = environment.archiveGraph

            check(trace.distinct().size == trace.size) { "Cyclic Partitions: '$trace' in extension: '${erm.name}'" }

            val loader = (loaders.get(partitionToLoad.type) as? ExtensionPartitionLoader<ExtensionPartitionMetadata>) ?: throw IllegalArgumentException(
                "Illegal partition type: '${partitionToLoad.type}', only accepted ones are: '${
                    loaders.objects().map(
                        Map.Entry<String, ExtensionPartitionLoader<*>>::key
                    )
                }'"
            )

            val dependencies = partitionToLoad.dependencies
                .map { dependency ->
                    val (dependencyDescriptor, dependencyResolver) = partitionToLoad.repositories.firstNotFailureOf findRepo@{ settings ->
                        val provider: DependencyResolverProvider<*, *, *> =
                            dependencyProviders.get(settings.type) ?: throw ArchiveException.ArchiveTypeNotFound(
                                settings.type,
                                trace()
                            )

                        val depReq: ArtifactRequest<*> = provider.parseRequest(dependency) ?: casuallyFail(
                            ArchiveException.DependencyInfoParseFailed(
                                "Failed to parse request: '$dependency'",
                                trace()
                            )
                        )

                        val repoSettings = provider.parseSettings(settings.settings) ?: casuallyFail(
                            ArchiveException.DependencyInfoParseFailed(
                                "Failed to parse settings: '$settings'",
                                trace()
                            )
                        )

                        val resolver =
                            provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>

                        archiveGraph.cache(
                            depReq as ArtifactRequest<ArtifactMetadata.Descriptor>,
                            repoSettings,
                            resolver
                        )().casuallyAttempt()

                        Result.success(
                            depReq.descriptor to resolver
                        )
                    }.merge()

                    archiveGraph.get(
                        dependencyDescriptor,
                        dependencyResolver
                    )().merge()
                }.filter {
                    val d = it.descriptor as? SimpleMavenDescriptor ?: return@filter true

                    // TODO this ensures some amount of backwards compatibility (as ext-loader or the client-api wont be reloaded) however this should instead be anything it in the class loader hierarchy.
                    !((d.group == EXT_LOADER_GROUP && d.artifact == EXT_LOADER_ARTIFACT) ||
                            (d.group == "net.yakclient" && d.artifact == "client-api"))
                }

            val metadata = getMetadata(partitionToLoad)().merge()

            loader.load(
                metadata,
                getArchive(partitionToLoad.path),
                object : PartitionLoaderHelper {
                    override val environment: ExtLoaderEnvironment = environment
                    override val runtimeModel: ExtensionRuntimeModel = erm
                    override val parents: List<ExtensionNode> = parents
                    override val parentClassloader: ClassLoader = this@ExtensionContainerLoader.parentClassloader
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
                                directTargets.add(ArchiveTarget(
                                    dependency.descriptor,
                                    ArchiveRelationship.Direct(
                                        ArchiveClassProvider(dependency.archive),
                                        ArchiveResourceProvider(dependency.archive),
                                    )
                                ))

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
        val partitions = loadPartitions(
            environment,
            ref,
            erm,
            parents,
        )().merge()

        ExtensionContainer(
            environment,
            ref,
            partitions
        ) { linker ->
            val handle = VersionedExtArchiveHandle(
                environment[ExtensionClassLoaderProvider].extract().createFor(
                    ref,
                    erm,
                    partitions.map {
                        it.node
                    },
                    parentClassloader,
                ),
                erm.name,
                parents.mapNotNullTo(HashSet()) { it.archive },
                ArchiveSourceProvider(ref).packages
            )

            val instance = partitions.map { it.node }.filterIsInstance<MainPartitionNode>().firstOrNull()?.extension
                ?: throw IllegalStateException("Failed to find main partition in extension: '${erm.descriptor}'")

            instance to handle
        } to partitions
    }
}