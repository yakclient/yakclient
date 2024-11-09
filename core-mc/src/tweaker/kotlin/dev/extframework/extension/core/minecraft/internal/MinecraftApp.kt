package dev.extframework.extension.core.minecraft.internal

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.info
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.findShortest
import dev.extframework.archive.mapper.newMappingsGraph
import dev.extframework.archive.mapper.transform.transformArchive
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.Archives
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.common.util.make
import dev.extframework.common.util.readInputStream
import dev.extframework.common.util.resolve
import dev.extframework.extension.core.environment.mixinAgentsAttrKey
import dev.extframework.extension.core.internal.InstrumentedAppImpl
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.mappingTargetAttrKey
import dev.extframework.extension.core.minecraft.util.write
import dev.extframework.extension.core.target.InstrumentedApplicationTarget
import dev.extframework.extension.core.target.TargetLinker
import dev.extframework.extension.core.util.withSlashes
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.environment.wrkDirAttrKey
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path

public fun MinecraftApp(
    instrumentedApp: InstrumentedApplicationTarget,
    environment: ExtensionEnvironment
): Job<ApplicationTarget> = job {
    val delegate = instrumentedApp.delegate

    val dir by environment[wrkDirAttrKey]

    val source = MojangMappingProvider.OBF_TYPE
    val destination by environment[mappingTargetAttrKey]

    val remappedPath: Path =
        dir.value resolve "remapped" resolve "minecraft" resolve destination.value.path resolve "minecraft-${delegate.node.descriptor.version}.jar"

    if (source == destination.value) return@job instrumentedApp

    if (remappedPath.make()) {
        val mappings: ArchiveMapping by lazy {
            newMappingsGraph(environment[mappingProvidersAttrKey].extract())
                .findShortest(source.identifier, destination.value.identifier)
                .forIdentifier(delegate.node.descriptor.version)
        }

        Archives.find(delegate.path, Archives.Finders.ZIP_FINDER).use { archive ->
            info("Remapping Minecraft from: '$source' to '${destination.value}'. This may take a second.")
            transformArchive(
                archive,
                listOf(delegate.node.handle!!),
                mappings,
                source.identifier,
                destination.value.identifier,
            )

            val toRemove = ArrayList<String>()
            archive.reader.entries()
                .filter { it.name.startsWith("META-INF") }
                .forEach { toRemove.add(it.name) }

            toRemove.forEach(archive.writer::remove)

            archive.write(remappedPath)
        }

    }

    val reference = Archives.find(remappedPath, Archives.Finders.ZIP_FINDER)

    val sources = object : SourceProvider {
        override val packages: Set<String> = setOf()

        private val delegateSources = ArchiveSourceProvider(reference)
        override fun findSource(name: String): ByteBuffer? {
            return delegateSources.findSource(name) ?: delegate.node.handle?.classloader?.getResourceAsStream(
                "${name.withSlashes()}.class"
            )?.let { ByteBuffer.wrap(it.readInputStream()) }
        }
    }

    val classLoader = IntegratedLoader(
        "Minecraft",
        sourceProvider = sources,
        resourceProvider = object : ResourceProvider {
            val mappedResourceDelegate = ArchiveResourceProvider(reference)
            val resourceDelegate = ArchiveResourceProvider(delegate.node.handle!!)
            override fun findResources(name: String): Sequence<URL> {
                return mappedResourceDelegate.findResources(name)
                    .takeUnless { it.toList().isEmpty() }
                    ?: resourceDelegate.findResources(name)
            }
        },

        // TODO platform class loader?
        parent = delegate.node.handle?.classloader?.parent ?: ClassLoader.getSystemClassLoader()
    )

    val mcApp = MinecraftApp(
        remappedPath,
        delegate,
        Archives.resolve(
            reference,
            classLoader,
            Archives.Resolvers.ZIP_RESOLVER,
            setOf(),
        ).archive
    )

   InstrumentedAppImpl(
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