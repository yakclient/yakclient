package net.yakclient.components.extloader.extension.partition

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveRelationship
import net.yakclient.boot.archive.ArchiveTarget
import net.yakclient.boot.loader.*
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionNode
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