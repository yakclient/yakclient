package dev.extframework.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.Resource
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.Archives
import dev.extframework.archives.zip.ZipFinder
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.constraint.registerConstraintNegotiator
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.basicObjectMapper
import dev.extframework.common.util.filterDuplicates
import dev.extframework.extloader.extension.ExtensionConstraintNegotiator
import dev.extframework.extloader.extension.ExtensionLoadException
import dev.extframework.extloader.extension.partition.artifact.PartitionRepositoryFactory
import dev.extframework.extloader.util.emptyArchiveReference
import dev.extframework.extloader.util.toInputStream
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.dependencyTypesAttrKey
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.environment.partitionLoadersAttrKey
import dev.extframework.tooling.api.extension.*
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.extension.partition.artifact.partitionNamed
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

public open class DefaultPartitionResolver(
    protected val environment: ExtensionEnvironment,
    private val bridge: ExtensionResolver.AccessBridge
) : PartitionResolver, RegisterAuditor {
    private val factory = PartitionRepositoryFactory { p, settings ->
        bridge.ermFor(p.extension).partitions.find() {
            it.name == p.partition
        }?.takeIf { bridge.repositoryFor(p.extension) == settings }
    }

    private val partitionLoaders
        get() = environment[partitionLoadersAttrKey].extract().container
    override val apiVersion: Int = TOOLING_API_VERSION
    override val context: ResolutionContext<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata>
        get() = factory.createContext()

    override fun register(auditors: Auditors): Auditors {
        return auditors.registerConstraintNegotiator(
            ExtensionConstraintNegotiator(
                PartitionDescriptor::class.java, {
                    "${it.extension.group}:${it.extension.artifact}:${it.partition}"
                }
            ) {
                SimpleMavenDescriptor(
                    it.extension.group,
                    it.extension.artifact,
                    it.extension.version,
                    null
                )
            }
        )
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
        archive: ArchiveReference?
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
        val archive = data.resources["partition.jar"]?.path?.let { Archives.find(it, ZipFinder) }
        val erm = bridge.ermFor(data.descriptor.extension)
        // Should never be null if getting to this stage.
        val prm = erm.partitions.find { it.name == data.descriptor.partition }!!

        val loader = getLoader(prm)
        val metadata = parseMetadata(loader, erm.partitions.find {
            it.name == prm.name
        } ?: throw PartitionLoadException(prm.name, "Partition not defined in the erm!") {
            erm.descriptor asContext "Extension"
        }, erm, archive)().merge()

        val parentLoader = bridge.classLoaderFor(data.descriptor.extension)

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

                override fun metadataFor(
                    partition: String
                ): Job<ExtensionPartitionMetadata> = job() {
                    parsedMetadata[erm.descriptor.partitionNamed(partition)]
                        ?: throw ExtensionLoadException(
                            data.descriptor.extension,
                            message = "Partition loader attempting to retrieve metadata for an extension partition that has not yet been cached."
                        ) {
                            solution("Cache the requested partition in the cache method of your partition loader.")

                            prm.name asContext "Partition name"
                            partition asContext "Requested partition name"
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
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val descriptor = artifact.metadata.descriptor
        val erm = bridge.ermFor(descriptor.extension)
        val prm by artifact.metadata::prm

//   TODO         erm.namedPartitions[descriptor.partition]
//            ?: throw PartitionLoadException(descriptor.partition, "Unknown partition: '${descriptor.partition}'. It was not defined by this extensions runtime model.") {
//                erm.descriptor asContext "Extension name"
//            }

        val loader = partitionLoaders.get(prm.type) ?: throw IllegalArgumentException(
            "Illegal partition type: '${prm.type}', only accepted ones are: '${
                partitionLoaders.objects().map(
                    Map.Entry<String, ExtensionPartitionLoader<*>>::key
                )
            }'"
        )

        helper.withResource(
            "prm.json",
            Resource("<heap>") {
                runCatching {
                    ByteArrayInputStream(basicObjectMapper.writeValueAsBytes(prm))
                }.mapException {
                    ExtensionLoadException(descriptor.extension, it) {
                        descriptor.partition asContext "Partition name"
                    }
                }.merge()
            }
        )

        helper.withResource("partition.jar", artifact.metadata.resource)

        val dependencyTypes = environment[dependencyTypesAttrKey].extract().container

        val dependencies = cachePartitionDependencies(
            prm,
            descriptor.extension.artifact,
            dependencyTypes,
            helper
        )().merge()

//        val resolvedParents = ermMetadata.parents.map resolveJob@ { parentInfo ->
//            val exceptions = parentInfo.candidates.map { candidate ->
//                val result = extensionRepositoryFactory
//                    .createNew(candidate)
//                    .get(parentInfo.request)()
//
//                if (result.isSuccess) return@resolveJob result.merge()
//
//                result
//            }.map { it.exceptionOrNull()!! }
//
//            if (exceptions.all { it is ArchiveException.ArchiveNotFound }) {
//                throw ArchiveException.ArchiveNotFound(
//                    helper.trace,
//                    parentInfo.request.descriptor,
//                    parentInfo.candidates
//                )
//            } else {
//                throw IterableException(
//                    "Failed to resolve extension parentInfo: '${parentInfo.request.descriptor}'",
//                    exceptions
//                )
//            }
//        }

        loader.cache(
            artifact,
            object : PartitionCacheHelper {
                //                override val parents: Map<ExtensionParent, ExtensionArtifactMetadata> =
//                    resolvedParents.associateBy { metadata: ExtensionArtifactMetadata ->
//                        ExtensionParent(
//                            metadata.descriptor.group,
//                            metadata.descriptor.artifact,
//                            metadata.descriptor.version,
//                        )
//                    }
                override val erm: ExtensionRuntimeModel = erm
                override val prm: PartitionRuntimeModel = prm

//                override fun newPartition(
//                    partition: PartitionRuntimeModel
//                ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
//                    return cache(
//                        Artifact(
//                            PartitionArtifactMetadata(
//                                PartitionDescriptor(
//                                    descriptor.extension,
//                                    partition.name,
//                                ),
//                                Resource("<heap:archive>") {
//                                    val ref = emptyArchiveReference()
//                                    ref.writer.put(
//                                        ArchiveReference.Entry(
//                                            "partition-tag.txt",
//                                            false,
//                                            ref
//                                        ) {
//                                            ByteArrayInputStream(partition.name.toByteArray())
//                                        }
//                                    )
//                                    ref.toInputStream()
//                                },
//                                partition
//                            ),
//                            listOf()
//                        ),
//                        this@DefaultPartitionResolver,
//                    )
//                }

                override fun cache(
                    reference: String
                ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
                    return cache(
                        PartitionArtifactRequest(
                            PartitionDescriptor(
                                descriptor.extension,
                                reference
                            )
                        ),
                        bridge.repositoryFor(artifact.metadata.descriptor.extension),
                        this@DefaultPartitionResolver,
                    )
                }

                override fun cache(
                    partition: String,
                    parent: ExtensionParent,
                ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob cacheJob@{
//                    val parentInfo = artifact.metadata.parents.find {
//                        it.request.descriptor == parent.toDescriptor()
//                    }
//                        ?: throw IllegalArgumentException("Extension: '${artifact.metadata.extension.descriptor}' does not define: '$parent' as an actual parent.")

                    cache(
                        PartitionArtifactRequest(PartitionDescriptor(parent.toDescriptor(), partition)),
                        bridge.repositoryFor(parent.toDescriptor()),
                        this@DefaultPartitionResolver
                    )().merge()

//                    cacheResult.exceptionOrNull()?.let { throwable ->
//                        if (throwable is ArchiveException.ArchiveNotFound) {
//                            throw throwable
//                        } else throw throwable
//                    }

//                    val exceptions = parentInfo.candidates.map { candidate ->
//                        val result = cache(
//                            PartitionArtifactRequest(parentInfo.request, partition),
//                            candidate,
//                            this@DefaultPartitionResolver
//                        )()
//
//                        if (result.isSuccess) return@cacheJob result.merge()
//
//                        result
//                    }.map { it.exceptionOrNull()!! }
//
//                    if (exceptions.all { it is ArchiveException.ArchiveNotFound }) {
//                        throw ArchiveException.ArchiveNotFound(
//                            helper.trace,
//                            parent.toDescriptor(),
//                            parentInfo.candidates
//                        )
//                    } else {
//                        throw IterableException("Failed to resolve extension parent: '$parent'", exceptions)
//                    }
                }

                // Delegation
                override val trace: ArchiveTrace by helper::trace

                override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
                    request: T,
                    repository: R,
                    resolver: ArchiveNodeResolver<D, T, *, R, *>
                ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
                    return helper.cache(request, repository, resolver)
                }

                override fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
                    artifact: Artifact<M>,
                    resolver: ArchiveNodeResolver<D, *, *, *, M>
                ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
                    return helper.cache(artifact, resolver)
                }

                override fun newData(
                    descriptor: PartitionDescriptor,
                    parents: List<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
                ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
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