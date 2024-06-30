package dev.extframework.components.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveRelationship
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.loader.*
import dev.extframework.components.extloader.api.extension.ExtensionRuntimeModel
import dev.extframework.components.extloader.api.extension.partition.ExtensionPartitionNode
import java.security.ProtectionDomain

internal fun PartitionClassLoader(
    model: ExtensionRuntimeModel,
    name: String,

    access: ArchiveAccessTree,
    ref: ArchiveReference,

    parent: ClassLoader,

    classProvider: ClassProvider = DelegatingClassProvider(access.targets.map { it.relationship.classes }),
    resourceProvider: ResourceProvider = ArchiveResourceProvider(ref),
    sourceProvider: SourceProvider = ArchiveSourceProvider(ref),
    sourceDefiner: SourceDefiner = SourceDefiner { n, b, cl, d ->
        d(n, b, ProtectionDomain(null, null, cl, null))
    },
): ClassLoader = IntegratedLoader(
    "${model.name}-${name}",

    classProvider = classProvider,
    resourceProvider = resourceProvider,
    sourceProvider = sourceProvider,
    sourceDefiner = sourceDefiner,

    parent = parent
)

internal fun PartitionArchiveHandle(
    name: String,
    model: ExtensionRuntimeModel,
    classLoader: ClassLoader,
    ref: ArchiveReference,
    parents: Set<ArchiveHandle>
) = object : ArchiveHandle {
    override val classloader: ClassLoader = classLoader
    override val name: String = model.name + "=" + name
    override val packages: Set<String> = ref.packages
    override val parents: Set<ArchiveHandle> = parents
}

public fun ExtensionPartitionNode.directTarget(
    descriptor: ArtifactMetadata.Descriptor
): ArchiveTarget = ArchiveTarget(
    descriptor,
    ArchiveRelationship.Direct(
        ArchiveClassProvider(archive),
        ArchiveResourceProvider(archive)
    )
)