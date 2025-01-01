package dev.extframework.tests.core

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.Archives
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.*
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.loader.*
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.common.util.readInputStream
import dev.extframework.core.minecraft.api.MinecraftAppApi
import dev.extframework.minecraft.bootstrapper.MinecraftProviderFinder
import dev.extframework.tooling.api.target.ApplicationDescriptor
import dev.extframework.tooling.api.target.ApplicationTarget
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.toPath

fun createEmptyApp(): ApplicationTarget {
    return object : ApplicationTarget {
        override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
            object : ClassLoadedArchiveNode<ApplicationDescriptor> {
                private val appDesc = ApplicationDescriptor.parseDescription("test:app:1.21")!!
                override val access: ArchiveAccessTree = object : ArchiveAccessTree {
                    override val descriptor: ArtifactMetadata.Descriptor = appDesc
                    override val targets: List<ArchiveTarget> = listOf()

                }
                override val descriptor: ApplicationDescriptor = appDesc
                override val handle: ArchiveHandle? = null
            }
        override val path: Path = Path("")
    }
}

fun createBlackboxApp(path: Path): ApplicationTarget {
    return object : ApplicationTarget {
        override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
            object : ClassLoadedArchiveNode<ApplicationDescriptor> {
                private val appDesc = ApplicationDescriptor.parseDescription("test:app:1")!!
                override val access: ArchiveAccessTree = object : ArchiveAccessTree {
                    override val descriptor: ArtifactMetadata.Descriptor = appDesc
                    override val targets: List<ArchiveTarget> = listOf()
                }
                override val descriptor: ApplicationDescriptor = appDesc
                override val handle: ArchiveHandle = run {
                    val ref = Archives.find(path, Archives.Finders.ZIP_FINDER)
                    Archives.resolve(
                        ref,
                        ArchiveClassLoader(ref, access, ClassLoader.getSystemClassLoader()),
                        Archives.Resolvers.ZIP_RESOLVER
                    ).archive
                }
            }
        override val path: Path = path
    }
}

fun createMinecraftApp(
    path: Path,
    version: String,
    archiveGraph: ArchiveGraph,
    dependencyTypes: DependencyTypeContainer,
): Job<ApplicationTarget> = job {
    class AppTarget(
        private val delegate: ClassLoader,
        version: String, override val path: Path,
        access: ArchiveAccessTree,
        classpath: List<Path>,
        override val gameDir: Path,
        override val gameJar: Path,
    ) : MinecraftAppApi(classpath) {
        override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
            object : ClassLoadedArchiveNode<ApplicationDescriptor> {
                private val appDesc = ApplicationDescriptor(
                    "net.minecraft",
                    "minecraft",
                    version,
                    "client"
                )
                override val descriptor: ApplicationDescriptor = appDesc
                override val access: ArchiveAccessTree = access
                override val handle: ArchiveHandle = classLoaderToArchive(
                    MutableClassLoader(
                        name = "minecraft-loader",
                        resources = MutableResourceProvider(
                            mutableListOf(
                                object : ResourceProvider {
                                    override fun findResources(name: String): Sequence<URL> {
                                        return delegate.getResources(name).asSequence()
                                    }
                                }
                            )),
                        sources = object : MutableSourceProvider(
                            mutableListOf(
                                object : SourceProvider {
                                    override val packages: Set<String> = setOf("*")

                                    override fun findSource(name: String): ByteBuffer? {
                                        val stream = delegate.getResourceAsStream(name.replace(".", "/") + ".class")
                                            ?: return null

                                        val bytes = stream.readInputStream()
                                        return ByteBuffer.wrap(bytes)
                                    }
                                }
                            )) {
                            override fun findSource(name: String): ByteBuffer? =
                                ((packageMap[name.substring(0, name.lastIndexOf('.').let { if (it == -1) 0 else it })]
                                    ?: listOf()) +
                                        (packageMap["*"] ?: listOf())).firstNotNullOfOrNull { it.findSource(name) }
                        },
                        parent = ClassLoader.getSystemClassLoader(),
                    )
                )
            }
    }

    val node = dev.extframework.minecraft.bootstrapper.loadMinecraft(
        version,
        SimpleMavenRepositorySettings.local(), // SimpleMavenRepositorySettings.default(url = "https://maven.extframework.dev/snapshots"),
        path,
        archiveGraph,
        dependencyTypes.get("simple-maven")!!.resolver as MavenLikeResolver<ClassLoadedArchiveNode<SimpleMavenDescriptor>, *>,
        object : MinecraftProviderFinder {
            override fun find(version: String): SimpleMavenDescriptor {
                return SimpleMavenDescriptor.parseDescription("dev.extframework.minecraft:minecraft-provider-def:2.0.14-SNAPSHOT")!!
            }
        }
    )().merge()

    val loader = IntegratedLoader(
        name = "Minecraft",
        resourceProvider = MutableResourceProvider(
            (node.libraries.map { it.archive } + node.archive)
                .mapTo(ArrayList()) { ArchiveResourceProvider(it) }
        ),
        sourceProvider = MutableSourceProvider(
            (node.libraries.map { it.archive } + node.archive)
                .mapTo(ArrayList()) { ArchiveSourceProvider(it) }
        ),
        parent = ClassLoader.getSystemClassLoader(),
    )

    AppTarget(
        loader,
        version,
        Paths.get(node.archive.location),
        node.access,
        listOf(node.archive.location.toPath()) + node.libraries.map { it.archive.location.toPath() },
        path,
        node.archive.location.toPath()
    )
}