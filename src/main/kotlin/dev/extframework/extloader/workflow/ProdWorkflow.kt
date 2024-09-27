package dev.extframework.extloader.workflow

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.ExtensionLoadException
import dev.extframework.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.extension.ExtensionNode
import dev.extframework.internal.api.extension.ExtensionNodeObserver
import dev.extframework.internal.api.extension.ExtensionResolver
import dev.extframework.internal.api.extension.ExtensionRunner
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

public data class ProdWorkflowContext(
    val extensions: Map<ExtensionDescriptor, ExtensionRepositorySettings>
) : WorkflowContext

public class ProdWorkflow : Workflow<ProdWorkflowContext> {
    override val name: String = "production"

    override fun work(context: ProdWorkflowContext, environment: ExtensionEnvironment): Job<Unit> = job {
        // Create initial environment
        environment += dev.extframework.extloader.environment.CommonEnvironment(environment[wrkDirAttrKey].extract().value)

        // Add dev graph to environment
        environment += DefaultExtensionResolver(
            environment[parentCLAttrKey].extract().value,
            environment,
        )

        fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
            return node.access.targets.map { it.relationship.node }
                .filterIsInstance<ExtensionNode>()
                .flatMapTo(HashSet(), ::allExtensions) + node
        }

        // Get extension resolver
        val extensionResolver = environment[ExtensionResolver].extract()

        fun loadTweakers(
            artifact: Artifact<ExtensionArtifactMetadata>
        ): AsyncJob<List<ExtensionPartitionContainer<TweakerPartitionNode, *>>> = asyncJob {
            val parents =
                artifact.parents.mapAsync {
                    loadTweakers(it)().merge()
                }

            val tweakerContainer: ExtensionPartitionContainer<TweakerPartitionNode, *>? = run {
                val descriptor = PartitionDescriptor(artifact.metadata.descriptor, TweakerPartitionLoader.TYPE)

                val cacheResult = environment.archiveGraph.cacheAsync(
                    PartitionArtifactRequest(descriptor),
                    artifact.metadata.repository,
                    extensionResolver.partitionResolver,
                )()
                if (cacheResult.isFailure && cacheResult.exceptionOrNull() is ArchiveException.ArchiveNotFound) return@run null
                else cacheResult.merge()

                environment.archiveGraph.get(
                    descriptor,
                    extensionResolver.partitionResolver,
                )().merge()
            } as? ExtensionPartitionContainer<TweakerPartitionNode, *>

            parents.awaitAll().flatten() + listOfNotNull(tweakerContainer)
        }

        val tweakers = job(JobName("Load tweakers")) {
            runBlocking(Dispatchers.IO) {
                context.extensions.flatMap { (ext, repo) ->
                    asyncJob {
                        val artifact = extensionResolver.createContext(repo)
                            .getAndResolveAsync(
                                ExtensionArtifactRequest(ext)
                            )().merge()

                        loadTweakers(artifact)().merge()
                    }().mapException {
                        ExtensionLoadException(ext, it) {
                            ext asContext "Extension"
                            this@ProdWorkflow.name asContext "Workflow/Environment"
                        }
                    }.merge()
                }
            }
        }().merge()

        tweakers.map { it.node }.forEach {
            it.tweaker.tweak(environment)().merge()
        }

        val extensionNodes = job(JobName("Load extensions")) {
            context.extensions.map { (ext, repo) ->
                job {
                    environment.archiveGraph.cache(
                        ExtensionArtifactRequest(
                            ext,
                        ),
                        repo,
                        extensionResolver
                    )().merge()

                    environment.archiveGraph.get(
                        ext,
                        extensionResolver
                    )().merge()
                }().mapException {
                    ExtensionLoadException(ext, it) {
                        ext asContext "Extension"
                        this@ProdWorkflow.name asContext "Workflow/Environment"
                    }
                }.merge()
            }
        }().merge()

        // Get all extension nodes in order
        val extensions = extensionNodes.flatMap(::allExtensions)

        // Get extension observer (if there is one after tweaker application) and observer each node
        environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }

        // Call init on all extensions, this is ordered correctly
        extensions.forEach {
            environment[ExtensionRunner].extract().init(it)().merge()
        }

    }
}