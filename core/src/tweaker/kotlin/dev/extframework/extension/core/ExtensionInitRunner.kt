package dev.extframework.extension.core

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.DelegatingResource
import dev.extframework.boot.loader.ArchiveClassProvider
import dev.extframework.boot.loader.ArchiveResourceProvider
import dev.extframework.boot.loader.DelegatingClassProvider
import dev.extframework.boot.loader.DelegatingResourceProvider
import dev.extframework.extension.core.exception.CoreExceptions
import dev.extframework.extension.core.mixin.DefaultMixinSubsystem
import dev.extframework.extension.core.mixin.MixinProcessContext
import dev.extframework.extension.core.mixin.MixinSubsystem
import dev.extframework.extension.core.partition.MainPartitionMetadata
import dev.extframework.extension.core.partition.MainPartitionNode
import dev.extframework.extension.core.target.TargetLinker
import dev.extframework.internal.api.exception.StructuredException
import dev.extframework.internal.api.extension.ExtensionNode
import dev.extframework.internal.api.extension.ExtensionRunner
import dev.extframework.internal.api.extension.descriptor
import dev.extframework.internal.api.extension.partition.ExtensionPartitionContainer

public class ExtensionInitRunner(
    private val mixinSubsystems: List<MixinSubsystem>,
    private val linker: TargetLinker
) : ExtensionRunner {
    // A map of our subsystems. Any or Object is included additionally as a reference to the default.

    override fun init(node: ExtensionNode): Job<Unit> = job {
        // Process mixins

        // TODO this creates inefficiencies as subsystems will all independently have to iterate over all
        //  classes to see if they use their subsystem. Instead the mixin process context should be expanded
        //  to include a 'node' parameter in which they only process 1 item at a time.
        mixinSubsystems.forEach {
            it.process(MixinProcessContext(node))().merge()
        }

        // Add extension sources to Target linker

        linker.addExtensionClasses(DelegatingClassProvider(node.partitions.map { it.handle }
            .map { ArchiveClassProvider(it) }))
        linker.addExtensionResources(DelegatingResourceProvider(node.partitions.map { it.handle }
            .map { ArchiveResourceProvider(it) }))

        // Run init on main partitions

        val mainPartition = (node.partitions.find {
            it.metadata.name == "main"
        } as? ExtensionPartitionContainer<MainPartitionNode, MainPartitionMetadata>)
            ?: throw IllegalStateException("Cannot find main partition")

        result {
            mainPartition.node.instance.init()
        }.mapException {
            StructuredException(
                CoreExceptions.ExtensionInitializationException,
                cause = it,
                message = "Exception initializing extension"
            ) {
                node.runtimeModel.descriptor.name asContext "Extension name"
            }
        }.getOrThrow()
    }
}