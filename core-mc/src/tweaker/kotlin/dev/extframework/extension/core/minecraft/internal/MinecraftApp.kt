package dev.extframework.extension.core.minecraft.internal

import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.findShortest
import dev.extframework.archive.mapper.newMappingsGraph
import dev.extframework.archive.mapper.transform.transformArchive
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.Archives
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.extension.core.environment.mixinAgentsAttrKey
import dev.extframework.extension.core.internal.InstrumentedAppImpl
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.mappingTargetAttrKey
import dev.extframework.extension.core.minecraft.util.write
import dev.extframework.extension.core.target.TargetLinker
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.environment.wrkDirAttrKey
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.file.Path

public fun MinecraftApp(
    delegate: ApplicationTarget,
    environment: ExtensionEnvironment
): ApplicationTarget {
    val dir by environment[wrkDirAttrKey]

    val source = MojangMappingProvider.OBF_TYPE
    val destination = environment[mappingTargetAttrKey].extract().value

    val remappedPath: Path =
        dir.value resolve "remapped" resolve "minecraft" resolve destination.path resolve "minecraft.jar"

    if (source == destination) return delegate

    val targetHandles = delegate.node.access.targets
        .map { it.relationship.node }
        .filterIsInstance<ClassLoadedArchiveNode<*>>()
        .mapNotNull { it.handle }

    val reference: ArchiveReference = if (remappedPath.make()) {
        val mappings: ArchiveMapping by lazy {
            newMappingsGraph(environment[mappingProvidersAttrKey].extract())
                .findShortest(source.identifier, destination.identifier)
                .forIdentifier(delegate.node.descriptor.version)
        }

        val archive = Archives.find(delegate.path, Archives.Finders.ZIP_FINDER)

        transformArchive(
            archive,
            targetHandles,
            mappings,
            source.identifier,
            destination.identifier,
        )

        archive.write(remappedPath)

        archive
    } else Archives.find(delegate.path, Archives.Finders.ZIP_FINDER)

    val classLoader = IntegratedLoader(
        "Minecraft",
        sourceProvider = ArchiveSourceProvider(reference),
        classProvider = DelegatingClassProvider(targetHandles.map { ArchiveClassProvider(it) }),
        resourceProvider = DelegatingResourceProvider(
            listOf(ArchiveResourceProvider(reference)) + targetHandles.map {
                ArchiveResourceProvider(it)
            }
        ),
        parent = delegate.node.handle?.classloader?.parent ?: ClassLoader.getPlatformClassLoader()
    )

    val mcApp = MinecraftApp(
        remappedPath,
        delegate,
        Archives.resolve(
            reference,
            classLoader,
            Archives.Resolvers.ZIP_RESOLVER,
            targetHandles.toSet(),
        ).archive
    )

    return InstrumentedAppImpl(
        mcApp,
        environment[TargetLinker].extract(),
        environment[mixinAgentsAttrKey].extract()
    )
}

internal class MinecraftApp(
    override val path: Path,
    val delegate: ApplicationTarget,
    val archive: ArchiveHandle,
) : ApplicationTarget {
    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
            override val access: ArchiveAccessTree = delegate.node.access
            override val descriptor: ApplicationDescriptor = delegate.node.descriptor
            override val handle: ArchiveHandle = archive
        }
}