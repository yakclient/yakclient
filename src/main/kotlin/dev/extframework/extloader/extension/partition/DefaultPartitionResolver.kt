package dev.extframework.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.asResourceStream
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.Archives
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.basicObjectMapper
import dev.extframework.common.util.filterDuplicates
import dev.extframework.extloader.extension.ExtensionLoadException
import dev.extframework.extloader.extension.artifact.ExtensionRepositoryFactory
import dev.extframework.extloader.extension.partition.artifact.PartitionRepositoryFactory
import dev.extframework.extloader.util.emptyArchiveReference
import dev.extframework.extloader.util.toInputStream
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.extension.*
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.extension.partition.*
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.internal.api.extension.partition.artifact.partitionNamed
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.nio.file.Files

public open class DefaultPartitionResolver(
    private val extensionRepositoryFactory: ExtensionRepositoryFactory,
    protected val environment: ExtensionEnvironment,
    private val parentClassLoaderProvider: (ExtensionDescriptor) -> ExtensionClassLoader
) : PartitionResolver {
    private val factory = PartitionRepositoryFactory(extensionRepositoryFactory)
    private val partitionLoaders = environment[partitionLoadersAttrKey].extract().container

    override fun createContext(
        settings: ExtensionRepositorySettings
    ): ResolutionContext<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata> {
        return factory.createContext(settings)
    }

    private fun readData(
        data: ArchiveData<*, CachedArchiveResource>
    ) = object {
        operator fun component1() =
            basicObjectMapper.readValue<PartitionRuntimeModel>(Files.readAllBytes(data.resources["prm.json"]!!.path))

        operator fun component2() =
            basicObjectMapper.readValue<ExtensionRuntimeModel>(Files.readAllBytes(data.resources["erm.json"]!!.path))

        operator fun component3() =
            Archives.find(data.resources["partition.jar"]!!.path, Archives.Finders.ZIP_FINDER)

        operator fun component4() =
            basicObjectMapper.readValue<Map<String, String>>(Files.readAllBytes(data.resources["repository.json"]!!.path))
    }

    protected fun getLoader(prm: PartitionRuntimeModel): ExtensionPartitionLoader<ExtensionPartitionMetadata> =
        (partitionLoaders.get(prm.type) as? ExtensionPartitionLoader<ExtensionPartitionMetadata>)
            ?: throw IllegalArgumentException(
                "Illegal partition type: '${prm.type}', only accepted ones are: '${
                    partitionLoaders.objects().map(
                        Map.Entry<String, ExtensionPartitionLoader<*>>::key
                    )
                }'"
            )

    protected val parsedMetadata: MutableMap<PartitionDescriptor, ExtensionPartitionMetadata> = HashMap()

    protected fun parseMetadata(
        loader: ExtensionPartitionLoader<ExtensionPartitionMetadata>,
        prm: PartitionRuntimeModel,
        erm: ExtensionRuntimeModel,
        archive: ArchiveReference
    ): Job<ExtensionPartitionMetadata> = job {
        parsedMetadata[erm.descriptor.partitionNamed(prm.name)] ?: loader.parseMetadata(
            prm,
            archive,
            object : PartitionMetadataHelper {
                override val erm: ExtensionRuntimeModel = erm
            }
        )().merge().also {
            parsedMetadata[erm.descriptor.partitionNamed(prm.name)] = it
        }
    }

    override fun load(
        data: ArchiveData<PartitionDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<ExtensionPartitionContainer<*, *>> = job {
        val (prm, erm, archive, rawRepository) = readData(data)

        val repository = environment[dependencyTypesAttrKey]
            .extract()
            .container
            .get("simple-maven")!!
            .parseSettings(rawRepository) as ExtensionRepositorySettings

        val loader = getLoader(prm)
        val metadata = parseMetadata(loader, prm, erm, archive)().merge()

        val parentLoader = parentClassLoaderProvider(data.descriptor.extension)

        loader.load(
            metadata,
            archive,
            object : PartitionAccessTree {
                override val partitions: List<ExtensionPartitionContainer<*, *>> =
                    accessTree.targets.map { it.relationship.node }
                        .filterIsInstance<ExtensionPartitionContainer<*, *>>()

                override val descriptor: ArtifactMetadata.Descriptor = data.descriptor
                override val targets: List<ArchiveTarget> = accessTree.targets
            },
            object : PartitionLoaderHelper {
                override val parentClassLoader: ClassLoader = parentLoader
                override val erm: ExtensionRuntimeModel = erm

                override fun metadataFor(reference: PartitionModelReference): AsyncJob<ExtensionPartitionMetadata> =
                    asyncJob {
                        parsedMetadata[erm.descriptor.partitionNamed(reference.name)] ?: run {
                            val result = environment.archiveGraph.cacheAsync(
                                PartitionArtifactRequest(erm.descriptor.partitionNamed(reference.name)),
                                repository,
                                this@DefaultPartitionResolver
                            )().merge()

                            val (prm2, erm2, archive2) = readData(result.item.value)

                            val loader2 = getLoader(prm2)

                            parseMetadata(loader2, prm2, erm2, archive2)().merge()
                        }
                    }

                override fun get(name: String): CachedArchiveResource? {
                    return data.resources[name]
                }
            }
        )().merge().also {
            parentLoader.partitions.add(it)
        }
    }

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: CacheHelper<PartitionDescriptor>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val loader = partitionLoaders.get(artifact.metadata.prm.type) ?: throw IllegalArgumentException(
            "Illegal partition type: '${artifact.metadata.prm.type}', only accepted ones are: '${
                partitionLoaders.objects().map(
                    Map.Entry<String, ExtensionPartitionLoader<*>>::key
                )
            }'"
        )

        helper.withResource(
            "prm.json",
            DelegatingResource("<heap>") {
                runCatching {
                    ByteArrayInputStream(basicObjectMapper.writeValueAsBytes(artifact.metadata.prm)).asResourceStream()
                }.mapException {
                    ExtensionLoadException(artifact.metadata.descriptor.extension, it) {
                        artifact.metadata.descriptor.partition asContext "Partition name"
                    }
                }.merge()
            }
        )
        helper.withResource(
            "erm.json",
            DelegatingResource("<heap>") {
                runCatching {
                    ByteArrayInputStream(basicObjectMapper.writeValueAsBytes(artifact.metadata.extension.erm)).asResourceStream()
                }.mapException {
                    ExtensionLoadException(artifact.metadata.descriptor.extension, it) {
                        artifact.metadata.descriptor.partition asContext "Partition name"
                    }
                }.merge()
            }
        )
        helper.withResource(
            "repository.json",
            DelegatingResource("<heap>") {
                val repository = artifact.metadata.extension.repository

                val (releases, snapshots) = (repository.layout as? SimpleMavenDefaultLayout)?.let {
                    it.releasesEnabled to it.snapshotsEnabled
                } ?: (true to true)
                runCatching {
                    ByteArrayInputStream(
                        basicObjectMapper.writeValueAsBytes(
                            mapOf(
                                "releasesEnabled" to releases,
                                "releasesEnabled" to snapshots,
                                "location" to repository.layout.location,
                                "preferredHash" to repository.preferredHash.name,
                                "type" to if (repository.layout is SimpleMavenDefaultLayout) "default" else "local"
                            )
                        )
                    ).asResourceStream()
                }.mapException {
                    ExtensionLoadException(artifact.metadata.descriptor.extension, it) {
                        artifact.metadata.descriptor.partition asContext "Partition name"
                    }
                }.merge()
            }
        )
        helper.withResource("partition.jar", artifact.metadata.resource)

        val dependencyTypes = environment[dependencyTypesAttrKey].extract().container

        val dependencies = cachePartitionDependencies(
            artifact.metadata.prm,
            artifact.metadata.extension.erm.name,
            dependencyTypes,
            helper
        )().merge()

        loader.cache(
            artifact,
            object : PartitionCacheHelper {
                override val parents: Map<ExtensionParent, ExtensionArtifactMetadata>
                override val erm: ExtensionRuntimeModel = artifact.metadata.extension.erm

                init {
                    val resolvedParents = artifact.metadata.extension.parents.map resolveJob@{ parentInfo ->
                        val exceptions = parentInfo.candidates.map { candidate ->
                            val result = extensionRepositoryFactory
                                .createNew(candidate)
                                .get(parentInfo.request)()

                            if (result.isSuccess) return@resolveJob result.merge()

                            result
                        }.map { it.exceptionOrNull()!! }

                        if (exceptions.all { it is ArchiveException.ArchiveNotFound }) {
                            throw ArchiveException.ArchiveNotFound(
                                helper.trace,
                                parentInfo.request.descriptor,
                                parentInfo.candidates
                            )
                        } else {
                            throw IterableException(
                                "Failed to resolve extension parent: '${parentInfo.request.descriptor}'",
                                exceptions
                            )
                        }
                    }

                    parents = resolvedParents.associateBy { metadata: ExtensionArtifactMetadata ->
                        ExtensionParent(
                            metadata.descriptor.group,
                            metadata.descriptor.artifact,
                            metadata.descriptor.version,
                        )
                    }
                }

                override fun newPartition(
                    partition: PartitionRuntimeModel
                ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> {
                    return cache(
                        Artifact(
                            PartitionArtifactMetadata(
                                PartitionDescriptor(
                                    artifact.metadata.descriptor.extension,
                                    partition.name,
                                ),
                                Resource("<heap:archive>") {
                                    val ref = emptyArchiveReference()
                                    ref.writer.put(
                                        ArchiveReference.Entry(
                                            "partition-tag.txt",
                                            Resource("<heap>") {
                                                ByteArrayInputStream(partition.name.toByteArray())
                                            },
                                            false,
                                            ref
                                        )
                                    )
                                    ref.toInputStream()
                                },
                                partition,
                                artifact.metadata.extension,
                            ),
                            listOf()
                        ),
                        this@DefaultPartitionResolver,
                    )
                }

                override fun cache(
                    reference: PartitionModelReference
                ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> {
                    return cache(
                        PartitionArtifactRequest(
                            PartitionDescriptor(
                                artifact.metadata.descriptor.extension,
                                reference.name
                            )
                        ),
                        artifact.metadata.extension.repository,
                        this@DefaultPartitionResolver,
                    )
                }

                override fun cache(
                    parent: ExtensionParent,
                    partition: PartitionModelReference
                ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob cacheJob@{
                    val parentInfo = artifact.metadata.extension.parents.find {
                        it.request.descriptor == parent.toDescriptor()
                    }
                        ?: throw IllegalArgumentException("Extension: '${artifact.metadata.extension.descriptor}' does not define: '$parent' as an actual parent.")

                    val exceptions = parentInfo.candidates.map { candidate ->
                        val result = cache(
                            PartitionArtifactRequest(parentInfo.request, partition.name),
                            candidate,
                            this@DefaultPartitionResolver
                        )()

                        if (result.isSuccess) return@cacheJob result.merge()

                        result
                    }.map { it.exceptionOrNull()!! }

                    if (exceptions.all { it is ArchiveException.ArchiveNotFound }) {
                        throw ArchiveException.ArchiveNotFound(
                            helper.trace,
                            parent.toDescriptor(),
                            parentInfo.candidates
                        )
                    } else {
                        throw IterableException("Failed to resolve extension parent: '$parent'", exceptions)
                    }
                }

                // Delegation

                override val trace: ArchiveTrace by helper::trace

                override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings, M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> cache(
                    request: T,
                    repository: R,
                    resolver: ArchiveNodeResolver<D, T, *, R, M>
                ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> {
                    return helper.cache(request, repository, resolver)
                }

                override fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
                    artifact: Artifact<M>,
                    resolver: ArchiveNodeResolver<D, *, *, *, M>
                ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> {
                    return helper.cache(artifact, resolver)
                }

                override fun newData(
                    descriptor: PartitionDescriptor,
                    parents: List<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>>
                ): Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>> {
                    return runBlocking { // Decide if it is needed to await for dependencies here or if the await should be moved to the initializer.
                        val fullParents = (parents + dependencies.awaitAll()).filterDuplicates()

                        helper.newData(descriptor, fullParents)
                    }
                }

                override fun withResource(name: String, resource: Resource) {
                    return helper.withResource(name, resource)
                }
            }
        )().merge()
    }
}