package dev.extframework.extloader.extension

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.asResourceStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.LazyMap
import dev.extframework.extloader.environment.ExtraAuditorsAttribute
import dev.extframework.extloader.extension.artifact.*
import dev.extframework.extloader.extension.partition.DefaultPartitionResolver
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.extension.ExtensionClassLoader
import dev.extframework.internal.api.extension.ExtensionNode
import dev.extframework.internal.api.extension.ExtensionResolver
import dev.extframework.internal.api.extension.ExtensionRuntimeModel
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
import kotlinx.coroutines.awaitAll
import java.io.ByteArrayInputStream
import java.nio.file.Files

public open class DefaultExtensionResolver(
    parent: ClassLoader,
    private val environment: ExtensionEnvironment,
) : ExtensionResolver {
    private val layerLoader = ExtensionLayerClassLoader(parent)
    protected val factory: ExtensionRepositoryFactory = ExtensionRepositoryFactory(environment[dependencyTypesAttrKey].extract().container)
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    protected val extensionLoaders: Map<ExtensionDescriptor, ExtensionClassLoader> = LazyMap {
        ExtensionClassLoader(
            it.name,
            ArrayList(),
            layerLoader,
        )
    }

    override val partitionResolver: DefaultPartitionResolver = DefaultPartitionResolver(
        factory,
        environment
    ) {
        extensionLoaders[it]!!
    }

    override val auditors: Auditors
        get() = (environment[dependencyTypesAttrKey]
            .extract().container.objects().values
            .map { it.resolver.auditors } + (environment[ExtraAuditorsAttribute].getOrNull()?.auditors ?: Auditors()))
        .fold(Auditors()) { acc, it ->
            it.auditors.values.fold(acc) { innerAcc, innerIt ->
                innerAcc.chain(innerIt)
            }
        }

    override fun createContext(settings: SimpleMavenRepositorySettings): ResolutionContext<ExtensionRepositorySettings, ExtensionArtifactRequest, ExtensionArtifactMetadata> {
        return factory.createContext(settings)
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

        val parents = accessTree.targets
            .map(ArchiveTarget::relationship)
            .filterIsInstance<ArchiveRelationship.Direct>()
            .map(ArchiveRelationship.Direct::node)
            .filterIsInstance<ExtensionNode>()

        ExtensionNode(
            data.descriptor,
            accessTree,
            parents,
            extensionLoaders[data.descriptor]!!,
            erm
        )
    }

    override fun cache(
        artifact: Artifact<ExtensionArtifactMetadata>,
        helper: CacheHelper<ExtensionDescriptor>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        helper.withResource(
            "erm.json",
            DelegatingResource("<heap>") {
                runCatching {
                    ByteArrayInputStream(mapper.writeValueAsBytes(artifact.metadata.erm)).asResourceStream()
                }.mapException {
                    ExtensionLoadException(artifact.metadata.descriptor, it) {}
                }.merge()
            }
        )

        val parents = artifact.parents.mapAsync {
            helper.cache(
                it,
                this@DefaultExtensionResolver
            )().merge()
        }

        val repositorySettings = artifact.metadata.repository
        val partitions = artifact.metadata.erm.partitions.mapAsync {
            helper.cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        artifact.metadata.descriptor,
                        it.name
                    )
                ),
                repositorySettings,
                this@DefaultExtensionResolver.partitionResolver
            )().merge()
        }

        helper.newData(
            artifact.metadata.descriptor,

            (parents + partitions)
                .onEach { it.start() }
                .awaitAll()
        )
    }
}
