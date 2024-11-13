package dev.extframework.tooling.api.extension

import com.durganmcbroom.jobs.result
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.util.requireKeyInDescriptor
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.PartitionResolver
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

public interface ExtensionResolver : ArchiveNodeResolver<
        ExtensionDescriptor,
        ExtensionArtifactRequest,
        ExtensionNode,
        ExtensionRepositorySettings,
        ExtensionArtifactMetadata>, EnvironmentAttribute {
    public val partitionResolver : PartitionResolver

    override val name: String
        get() = "extension"
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionResolver

    override val metadataType: Class<ExtensionArtifactMetadata>
        get() = ExtensionArtifactMetadata::class.java
    override val nodeType: Class<ExtensionNode>
        get() = ExtensionNode::class.java

    public companion object : EnvironmentAttributeKey<ExtensionResolver>

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<ExtensionDescriptor> = result {
        ExtensionDescriptor.parseDescriptor(descriptor.requireKeyInDescriptor("descriptor") { trace })
    }

    override fun serializeDescriptor(
        descriptor: ExtensionDescriptor
    ): Map<String, String> {
        return mapOf(
            "descriptor" to descriptor.name,
        )
    }

    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
        return Path(
            descriptor.group.replace('.', File.separatorChar),
            descriptor.artifact,
            descriptor.version,
            "${descriptor.artifact}-${descriptor.version}-$classifier.$type"
        )
    }
}
