package dev.extframework.extloader.extension

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.Resource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.constraint.registerConstraintNegotiator
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.basicObjectMapper
import dev.extframework.extloader.extension.artifact.ExtensionRepositoryFactory
import dev.extframework.extloader.extension.partition.DefaultPartitionResolver
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.dependencyTypesAttrKey
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.extension.ExtensionClassLoader
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import kotlinx.coroutines.awaitAll
import java.io.ByteArrayInputStream
import java.nio.file.Files

public open class DefaultExtensionResolver(
    parent: ClassLoader,
    private val environment: ExtensionEnvironment,
) : ExtensionResolver, RegisterAuditor {
    private val layerLoader = ExtensionLayerClassLoader(parent)
    protected val factory: ExtensionRepositoryFactory = ExtensionRepositoryFactory(
        environment[dependencyTypesAttrKey].extract().container
    )

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    internal data class ExtensionLoadMetadata(
//        val erm: ExtensionRuntimeModel,
//        val repository: ExtensionRepositorySettings,
        val classloader: ExtensionClassLoader
    )

    internal data class ExtensionCacheMetadata(
        val erm: ExtensionRuntimeModel,
        val repository: ExtensionRepositorySettings,
    )


    internal val cachedExtensions : MutableMap<ExtensionDescriptor, ExtensionCacheMetadata> = HashMap()

    internal val loadedExtensions: MutableMap<ExtensionDescriptor, ExtensionLoadMetadata> = HashMap()

    override val apiVersion: Int = TOOLING_API_VERSION
    override val context: ResolutionContext<ExtensionRepositorySettings, ExtensionArtifactRequest, ExtensionArtifactMetadata>
        get() = factory.createContext()

    override val accessBridge: ExtensionResolver.AccessBridge = object : ExtensionResolver.AccessBridge {
        private fun extensionNotPresent(descriptor: ExtensionDescriptor): Nothing {
            throw ExtensionLoadException(
                descriptor,
                message = "Failed to load a partition because this extension was not loaded yet."
            ) {
                solution("Loading the extension tree before loading partitions.")
            }
        }

        override fun classLoaderFor(descriptor: ExtensionDescriptor): ExtensionClassLoader {
            return loadedExtensions[descriptor]?.classloader ?: extensionNotPresent(descriptor)
        }

        override fun ermFor(descriptor: ExtensionDescriptor): ExtensionRuntimeModel {
            return cachedExtensions[descriptor]?.erm ?: extensionNotPresent(descriptor)
        }

        override fun repositoryFor(descriptor: ExtensionDescriptor): ExtensionRepositorySettings {
            return cachedExtensions[descriptor]?.repository ?: extensionNotPresent(descriptor)
        }
    }

    override val partitionResolver: DefaultPartitionResolver = DefaultPartitionResolver(
        environment,
        accessBridge
    )

    override fun register(auditors: Auditors): Auditors {
        return auditors.registerConstraintNegotiator(
            ExtensionConstraintNegotiator(
                ExtensionDescriptor::class.java, {
                    "${it.group}:${it.artifact}"
                }
            ) {
                SimpleMavenDescriptor(it.group, it.artifact, it.version, null)
            })
    }

    override fun load(
        data: ArchiveData<ExtensionDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<ExtensionNode> = job {
        val erm = data.resources["erm.json"]!!.path.let {
            mapper.readValue<ExtensionRuntimeModel>(
                Files.readAllBytes(it)
            )
        }

        val rawRepository = data.resources["repository.json"]!!
            .path
            .toFile()
            .inputStream()
            .let { basicObjectMapper.readValue<Map<String, String>>(it) }

        val repository = environment[dependencyTypesAttrKey]
            .extract()
            .container
            .get("simple-maven")!!
            .parseSettings(rawRepository) as ExtensionRepositorySettings

        loadedExtensions[data.descriptor] = ExtensionLoadMetadata(
            ExtensionClassLoader(
                data.descriptor.name,
                ArrayList(),
                layerLoader,
            )
        )

        cachedExtensions[data.descriptor] = ExtensionCacheMetadata(
            erm,
            repository,
        )

        val parents = accessTree.targets
            .map(ArchiveTarget::relationship)
            .filterIsInstance<ArchiveRelationship.Direct>()
            .map(ArchiveRelationship.Direct::node)
            .filterIsInstance<ExtensionNode>()

        ExtensionNode(
            data.descriptor,
            accessTree,
            parents,
            loadedExtensions[data.descriptor]!!.classloader,
            erm
        )
    }

    override fun cache(
        artifact: Artifact<ExtensionArtifactMetadata>,
        helper: CacheHelper<ExtensionDescriptor>
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.withResource(
            "erm.json",
            Resource("<heap>") {
                runCatching {
                    ByteArrayInputStream(mapper.writeValueAsBytes(artifact.metadata.erm))
                }.mapException {
                    ExtensionLoadException(artifact.metadata.descriptor, it) {}
                }.getOrThrow()
            }
        )

        helper.withResource(
            "repository.json",
            Resource("<heap>") {
                val repository = artifact.metadata.repository

                runCatching {
                    ByteArrayInputStream(
                        basicObjectMapper.writeValueAsBytes(
                            mapOf(
                                "location" to repository.layout.location,
                                "preferredHash" to repository.preferredHash.name,
                                "type" to if (repository.layout is SimpleMavenDefaultLayout) "default" else "local"
                            )
                        )
                    )
                }.mapException {
                    ExtensionLoadException(artifact.metadata.descriptor, it) {
                        artifact.metadata.descriptor asContext "Extension name"
                    }
                }.merge()
            }
        )

        cachedExtensions[artifact.metadata.descriptor] = ExtensionCacheMetadata(
            artifact.metadata.erm,
            artifact.metadata.repository,
        )

        val parents = artifact.parents.mapAsync {
            helper.cache(
                it,
                this@DefaultExtensionResolver
            )().merge()
        }

//        val repositorySettings = artifact.metadata.repository
//        val partitions = artifact.metadata.erm.partitions.mapAsync {
//            helper.cache(
//                PartitionArtifactRequest(
//                    PartitionDescriptor(
//                        artifact.metadata.descriptor,
//                        it.name
//                    )
//                ),
//                repositorySettings,
//                this@DefaultExtensionResolver.partitionResolver
//            )().merge()
//        }

        helper.newData(
            artifact.metadata.descriptor,

            (parents
//                    + partitions
                    )
                .onEach { it.start() }
                .awaitAll()
        )
    }

}
