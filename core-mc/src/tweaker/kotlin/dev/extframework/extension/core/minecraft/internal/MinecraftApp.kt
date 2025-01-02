package dev.extframework.extension.core.minecraft.internal

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.info
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.findShortest
import dev.extframework.archive.mapper.newMappingsGraph
import dev.extframework.archive.mapper.transform.transformArchive
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.Archives
import dev.extframework.archives.zip.ZipFinder
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.core.minecraft.api.MinecraftAppApi
import dev.extframework.extension.core.environment.mixinAgentsAttrKey
import dev.extframework.extension.core.internal.InstrumentedAppImpl
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.mappingTargetAttrKey
import dev.extframework.extension.core.minecraft.util.write
import dev.extframework.extension.core.target.InstrumentedApplicationTarget
import dev.extframework.extension.core.target.TargetLinker
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.environment.wrkDirAttrKey
import dev.extframework.tooling.api.target.ApplicationDescriptor
import dev.extframework.tooling.api.target.ApplicationTarget
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.UUID
import java.util.jar.Manifest
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

public fun MinecraftApp(
    instrumentedApp: InstrumentedApplicationTarget,
    environment: ExtensionEnvironment
): Job<ApplicationTarget> = job {
    val delegate = instrumentedApp.delegate
    check(delegate is MinecraftAppApi) {
        "Invalid environment. The application target should be an instance of '${MinecraftAppApi::class.qualifiedName}'."
    }

    val dir by environment[wrkDirAttrKey]

    val source = MojangMappingProvider.OBF_TYPE
    val destination by environment[mappingTargetAttrKey]

    val remappedPath: Path =
        dir.value resolve "remapped" resolve "minecraft" resolve destination.value.path resolve delegate.node.descriptor.version
    val mappingsMarker = remappedPath resolve ".marker"

    if (source == destination.value) return@job instrumentedApp

    var gameJar = delegate.gameJar

    val classpath = if (!mappingsMarker.exists()) {
        val mappings: ArchiveMapping by lazy {
            newMappingsGraph(environment[mappingProvidersAttrKey].extract())
                .findShortest(source.identifier, destination.value.identifier)
                .forIdentifier(delegate.node.descriptor.version)
        }

        info("Remapping Minecraft from: '$source' to '${destination.value}'. This may take a second.")
        val remappedJars = delegate.classpath.mapNotNull { t ->
            val name = UUID.randomUUID().toString() + ".jar"
            val isGame = t == delegate.gameJar

            if (t.extension != "jar") {
                System.err.println(
                    "Found minecraft file on classpath: '$t' but it is not a jar. Will not remap it."
                )
                return@mapNotNull null
            }

            Archives.find(t, ZipFinder).use { archive ->
                transformArchive(
                    archive,
                    listOf(delegate.node.handle!!),
                    mappings,
                    source.identifier,
                    destination.value.identifier,
                )

                val manifest = archive.getResource("META-INF/MANIFEST.MF")

                if (manifest != null) {
                    val manifest = Manifest()

                    // Stripping checksums
                    stripManifestChecksums(
                        manifest,
                    )
                    val bytes = ByteArrayOutputStream().use {
                        manifest.write(it)
                        it
                    }.toByteArray()

                    archive.writer.put(
                        ArchiveReference.Entry(
                            "META-INF/MANIFEST.MF",
                            false,
                            archive
                        ) {
                            ByteArrayInputStream(bytes)
                        })
                }

                archive.reader
                    .entries()
                    .filter {it.name.startsWith("META-INF/") }
                    .forEach { entry ->
                        if (isSigningRelated(entry.name)) {
                            archive.writer.remove(entry.name)
                        }
                    }

                // Write out
                val path = remappedPath resolve name

                path.make()
                archive.write(path)

                if (isGame) {
                    gameJar = path
                }

                path to (if (isGame) "game" else "lib")
            }
        }

        mappingsMarker.make()
        mappingsMarker.writeLines(remappedJars.map { "${it.second}:${it.first}" })

        remappedJars.map { it.first }
    } else {
        mappingsMarker.readLines().map {
            val (type, path) = it.split(":")
            val pathObj = Path(path)

            if (type == "game") {
                gameJar = pathObj
            }

            pathObj
        }
    }

    val references = classpath.map { it -> Archives.find(it, ZipFinder) }

    val sources = DelegatingSourceProvider(
        references.map(::ArchiveSourceProvider)
    )

//        object : SourceProvider {
//        override val packages: Set<String> = setOf()
//
//        private val delegateSources = //ArchiveSourceProvider(Delegagtin)
//        override fun findSource(name: String): ByteBuffer? {
//            return delegateSources.findSource(name) ?: delegate.node.handle?.classloader?.getResourceAsStream(
//                "${name.withSlashes()}.class"
//            )?.let { ByteBuffer.wrap(it.readInputStream()) }
//        }
//    }

    val classLoader = IntegratedLoader(
        "Minecraft",
        sourceProvider = sources,
        resourceProvider = DelegatingResourceProvider(references.map(::ArchiveResourceProvider)),
//            object : ResourceProvider {
//            val mappedResourceDelegate = ArchiveResourceProvider(reference)
//            val resourceDelegate = ArchiveResourceProvider(delegate.node.handle!!)
//            override fun findResources(name: String): Sequence<URL> {
//                return mappedResourceDelegate.findResources(name)
//                    .takeUnless { it.toList().isEmpty() }
//                    ?: resourceDelegate.findResources(name)
//            }
//        },

        // TODO platform class loader?
        parent = delegate.node.handle?.classloader?.parent ?: ClassLoader.getSystemClassLoader()
    )

    val mcApp = MinecraftApp(
        remappedPath,
        delegate,
        classLoaderToArchive(classLoader),
        delegate.gameDir,
        gameJar,
        classpath
    )

    InstrumentedAppImpl(
        mcApp,
        environment[TargetLinker].extract(),
        environment[mixinAgentsAttrKey].extract()
    )
}

private fun stripManifestChecksums(
    manifest: Manifest
) {
    manifest.entries.clear()
}

// @see java.util.jar.JarVerifier#isSigningRelated
private fun isSigningRelated(
    name: String
): Boolean {
    return name.endsWith(".SF")
            || name.endsWith(".DSA")
            || name.endsWith(".RSA")
            || name.endsWith(".EC")
            || name.startsWith("SIG-")
}

internal class MinecraftApp(
    override val path: Path,
    val delegate: ApplicationTarget,
    val archive: ArchiveHandle,
    override val gameDir: Path,
    override val gameJar: Path,
    classpath: List<Path>,
) : MinecraftAppApi(classpath) {
    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
            override val access: ArchiveAccessTree = delegate.node.access
            override val descriptor: ApplicationDescriptor = delegate.node.descriptor
            override val handle: ArchiveHandle = archive
        }
}