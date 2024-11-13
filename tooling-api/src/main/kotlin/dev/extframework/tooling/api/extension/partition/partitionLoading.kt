package dev.extframework.tooling.api.extension.partition

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import java.security.ProtectionDomain

public fun PartitionClassLoader(
    descriptor: PartitionDescriptor,

    access: ArchiveAccessTree,
    ref: ArchiveReference,

    parent: ClassLoader,

    classProvider: ClassProvider = DelegatingClassProvider(
        access.targets
            .map { it.relationship.node }
            .mapNotNull { (it as? ClassLoadedArchiveNode)?.handle }
            .map(::ArchiveClassProvider)
    ),
    resourceProvider: ResourceProvider = ArchiveResourceProvider(ref),
    sourceProvider: SourceProvider = ArchiveSourceProvider(ref),
    sourceDefiner: SourceDefiner = SourceDefiner { n, b, cl, d ->
        d(n, b, ProtectionDomain(null, null, cl, null))
    },
): ClassLoader = IntegratedLoader(
    "$descriptor",

    classProvider = classProvider,
    resourceProvider = resourceProvider,
    sourceProvider = sourceProvider,
    sourceDefiner = sourceDefiner,

    parent = parent
)

public fun PartitionArchiveHandle(
    name: String,
    classLoader: ClassLoader,
    ref: ArchiveReference,
    parents: Set<ArchiveHandle>
): ArchiveHandle = object : ArchiveHandle {
    override val classloader: ClassLoader = classLoader
    override val name: String = name
    override val packages: Set<String> = ref.packages
    override val parents: Set<ArchiveHandle> = parents
}