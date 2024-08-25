package dev.extframework.extension.core.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.openStream
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveData
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.SourceProvider
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.core.api.Extension
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.exception.CoreExceptions
import dev.extframework.extension.core.feature.FeatureReference
import dev.extframework.extension.core.feature.definesFeatures
import dev.extframework.extension.core.feature.findDefinedFeatures
import dev.extframework.extension.core.util.parseNode
import dev.extframework.extension.core.util.toOrNull
import dev.extframework.extension.core.util.withDots
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.exception.StructuredException
import dev.extframework.internal.api.extension.ExtensionRepository
import dev.extframework.internal.api.extension.PartitionRuntimeModel
import dev.extframework.internal.api.extension.descriptor
import dev.extframework.internal.api.extension.partition.*
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.internal.api.extension.partition.artifact.partitionNamed
import kotlinx.coroutines.awaitAll
import java.nio.ByteBuffer
import java.util.*

public class MainPartitionLoader(
    private val environment: ExtensionEnvironment
) : ExtensionPartitionLoader<MainPartitionMetadata> {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "main"
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference,
        helper: PartitionMetadataHelper
    ): Job<MainPartitionMetadata> = job {
        val processor = environment[AnnotationProcessor].extract()

        val allFeatures = reference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .map {
                it.resource.openStream().parseNode()
            }.filter {
                it.definesFeatures(processor)
            }.flatMap {
                it.findDefinedFeatures(processor)
            }.toList()

        MainPartitionMetadata(
            allFeatures,
            partition.options["extension-class"]
                ?: throw IllegalArgumentException("Main partition from extension: '${helper.erm.descriptor}' must contain an extension class defined as option: 'extension-class'."),
            reference,
            partition.repositories,
            partition.dependencies
        )
    }

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val featurePartitionName = "feature-holder-${UUID.randomUUID()}"

        val featurePartition = PartitionRuntimeModel(
            FeaturePartitionLoader.TYPE,
            featurePartitionName,

            artifact.metadata.prm.repositories,
            artifact.metadata.prm.dependencies,
            mapOf()
        )

        val newPartition = helper.newPartition(
            featurePartition
        )().merge()

        val parents = helper.parents
            .mapNotNull {
                it.key toOrNull it.value.erm.partitions.find { p -> p.type == TYPE }
            }.mapAsync {
                helper.cache(it.first, it.second)().merge()
            }

        helper.newData(
            artifact.metadata.descriptor,
            parents.awaitAll() + newPartition
        )
    }

    override fun load(
        metadata: MainPartitionMetadata,
        reference: ArchiveReference,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): Job<ExtensionPartitionContainer<*, MainPartitionMetadata>> = job {
        val thisDescriptor = helper.erm.descriptor.partitionNamed(metadata.name)

        ExtensionPartitionContainer(
            thisDescriptor,
            metadata,
            run { // The reason we use a target enabled partition for this one is because main depends on the feature partition which is target enabled. It doesnt make sense for a non target enabled partition to rely on an enabled one.
                val sourceProviderDelegate = ArchiveSourceProvider(reference)

                val cl = PartitionClassLoader(
                    thisDescriptor,
                    accessTree,
                    reference,
                    helper.parentClassLoader,
                    sourceProvider = object : SourceProvider by sourceProviderDelegate {
                        private val featureContainers =
                            metadata.definedFeatures.mapTo(HashSet()) { it.container.withDots() }

                        override fun findSource(name: String): ByteBuffer? {
                            return if (featureContainers.contains(name)) null
                            else sourceProviderDelegate.findSource(name)
                        }
                    }
                )

                val handle = PartitionArchiveHandle(
                    "${helper.erm.name}-main",
                    cl,
                    reference,
                    setOf()
                )

                val extensionClassName = metadata.extensionClass

                val extensionClass = dev.extframework.common.util.runCatching(ClassNotFoundException::class) {
                    handle.classloader.loadClass(
                        extensionClassName
                    )
                } ?: throw StructuredException(
                    CoreExceptions.ExtensionClassNotFound,
                    message = "Could not init extension because the extension class couldn't be found."
                ) {
                    extensionClassName asContext "Extension class name"
                }

                val extensionConstructor =
                    dev.extframework.common.util.runCatching(NoSuchMethodException::class) { extensionClass.getConstructor() }
                        ?: throw Exception("Could not find no-arg constructor in class: '${extensionClassName}' in extension: '${helper.erm.name}'.")

                val instance = extensionConstructor.newInstance() as? Extension
                    ?: throw Exception("Extension class: '$extensionClass' does not extend: '${Extension::class.java.name}'.")

                MainPartitionNode(
                    handle,
                    accessTree,
                    instance
                )
            })
    }
}

public data class MainPartitionMetadata(
    val definedFeatures: List<FeatureReference>,
    val extensionClass: String,
    val archive: ArchiveReference,

    internal val repositories: List<ExtensionRepository>,
    internal val dependencies: Set<Map<String, String>>,
) : ExtensionPartitionMetadata {
    override val name: String = "main"
}

public data class MainPartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
    public val instance: Extension
) : ExtensionPartition