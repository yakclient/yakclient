package dev.extframework.components.extloader.workflow

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.environment.CommonEnvironment
import dev.extframework.components.extloader.extension.DefaultExtensionResolver
import dev.extframework.components.extloader.extension.ExtensionLoadException
import dev.extframework.components.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.components.extloader.extension.partition.TweakerPartitionNode
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
import java.nio.file.Files
import java.nio.file.Path

public data class DevWorkflowContext(
    val extension: ExtensionDescriptor,
    val repository: ExtensionRepositorySettings,
) : WorkflowContext

public class DevWorkflow : Workflow<DevWorkflowContext> {
    override val name: String = "extension-dev"

    override fun work(
        context: DevWorkflowContext,
        environment: ExtensionEnvironment
    ): Job<Unit> = job(JobName("Run extension dev workflow")) {
        // Create initial environment
        environment += CommonEnvironment(environment[wrkDirAttrKey].extract().value)

        // Add dev graph to environment
        environment += DevExtensionResolver(
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
                val artifact = extensionResolver.createContext(context.repository)
                    .getAndResolveAsync(
                        ExtensionArtifactRequest(context.extension)
                    )().merge()

                loadTweakers(artifact)().merge()
            }
        }().mapException {
            ExtensionLoadException(context.extension, it) {
                context.extension asContext "Extension"
                this@DevWorkflow.name asContext "Workflow/Environment"
            }
        }.merge()

        tweakers.map { it.node }.forEach {
            it.tweaker.tweak(environment)().merge()
        }

        val extensionNode = job(JobName("Load extensions")) {
            environment.archiveGraph.cache(
                ExtensionArtifactRequest(
                    context.extension,
                ),
                context.repository,
                extensionResolver
            )().merge()
            environment.archiveGraph.get(
                context.extension,
                extensionResolver
            )().merge()
        }().mapException {
            ExtensionLoadException(context.extension, it) {
                context.extension asContext "Extension"
                this@DevWorkflow.name asContext "Workflow/Environment"
            }
        }.merge()

        // Get all extension nodes in order
        val extensions = allExtensions(extensionNode)

        // Get extension observer (if there is one after tweaker application) and observer each node
        environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }

        // Call init on all extensions, this is ordered correctly
        extensions.forEach {
            environment[ExtensionRunner].extract().init(it)().merge()
        }
    }
}

private class DevExtensionResolver(
    parent: ClassLoader, environment: ExtensionEnvironment
) : DefaultExtensionResolver(
    parent, environment
) {
    private val tempDir = Files.createTempDirectory("dev-extensions").toAbsolutePath()

    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
        return tempDir resolve super.pathForDescriptor(descriptor, classifier, type)
    }
}