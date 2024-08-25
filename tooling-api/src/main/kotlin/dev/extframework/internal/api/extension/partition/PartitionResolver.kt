package dev.extframework.internal.api.extension.partition

import com.durganmcbroom.jobs.result
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.util.requireKeyInDescriptor
import dev.extframework.boot.util.typeOf
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
import java.io.File
import java.nio.file.Path

public interface PartitionResolver : ArchiveNodeResolver<
        PartitionDescriptor, PartitionArtifactRequest, ExtensionPartitionContainer<*, *>, ExtensionRepositorySettings, PartitionArtifactMetadata> {
    override val metadataType: Class<PartitionArtifactMetadata>
        get() = PartitionArtifactMetadata::class.java
    override val name: String
        get() = "extension-partition"
    override val nodeType: Class<in ExtensionPartitionContainer<*, *>>
        get() = typeOf()

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<PartitionDescriptor> = result {
        val desc = descriptor.requireKeyInDescriptor("descriptor") { trace }
        PartitionDescriptor.parseDescriptor(desc)
    }

    override fun serializeDescriptor(descriptor: PartitionDescriptor): Map<String, String> {
        return mapOf("descriptor" to descriptor.name)
    }

    override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
        return Path.of(
            "extensions",
            descriptor.extension.group.replace('.', File.separatorChar),
            descriptor.extension.artifact,
            descriptor.extension.version,
            descriptor.partition,
            "${descriptor.extension.artifact}${descriptor.extension.version}-${descriptor.partition}-$classifier.$type"
        )
    }
}