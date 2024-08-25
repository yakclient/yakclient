package dev.extframework.components.extloader.test

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.loader.*
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.common.util.readInputStream
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.extension.mapping.MojangExtensionMappingProvider
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import dev.extframework.minecraft.bootstrapper.MinecraftProviderFinder
import dev.extframework.minecraft.bootstrapper.loadMinecraft
import java.nio.ByteBuffer
import java.nio.file.Path

fun createAppTarget(
    version: String,
    path: Path,
    archiveGraph: ArchiveGraph,
    dependencyTypeContainer: DependencyTypeContainer
): Job<ApplicationTarget> = job {
    val handle = loadMinecraft(
        version,
        SimpleMavenRepositorySettings.local(),
        path resolve "minecraft",
        archiveGraph,
        dependencyTypeContainer.get("simple-maven")!!.resolver as MavenLikeResolver<ClassLoadedArchiveNode<SimpleMavenDescriptor>, *>,
        object : MinecraftProviderFinder {
            override fun find(version: String): SimpleMavenDescriptor {
                return SimpleMavenDescriptor.parseDescription("dev.extframework.minecraft:minecraft-provider-def:2.0.3-SNAPSHOT")!!
            }
        }
    )().merge()

    val loader = IntegratedLoader(
        name = "Minecraft",
        resourceProvider = handle.resources,
        sourceProvider = object : SourceProvider {
            override val packages: Set<String> = setOf()

            override fun findSource(name: String): ByteBuffer? {
                return handle.resources.findResources(name.replace('.', '/') + ".class")
                    .firstOrNull()
                    ?.openStream()
                    ?.readInputStream()
                    ?.let(ByteBuffer::wrap)
            }
        },
        parent = ClassLoader.getPlatformClassLoader()
    )

    TestAppTarget(loader, version)
}

private class TestAppTarget(
    private val delegate: ClassLoader,
    version: String,
) : ApplicationTarget {
    private val transformers: MutableList<(ByteArray) -> ByteArray> = ArrayList()

    override val mappingNamespace: String = MojangExtensionMappingProvider.OBFUSCATED
    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
            override val access: ArchiveAccessTree = object : ArchiveAccessTree {
                override val descriptor: ArtifactMetadata.Descriptor =
                    SimpleMavenDescriptor.parseDescription("test:app:$version")!!
                override val targets: List<ArchiveTarget> = emptyList()
            }
            override val descriptor: ApplicationDescriptor =
                SimpleMavenDescriptor.parseDescription("test:app:$version")!!
            override val handle: ArchiveHandle = classLoaderToArchive(
                MutableClassLoader(
                    name = "minecraft-loader",
                    sources = object : MutableSourceProvider(mutableListOf(
                        object : SourceProvider {
                            override val packages: Set<String> = setOf("*")

                            override fun findSource(name: String): ByteBuffer? {
                                val stream =
                                    delegate.getResourceAsStream(name.replace(".", "/") + ".class") ?: return null

                                val bytes = stream.readInputStream()
                                val transformedBytes = transformers.fold(bytes) { acc, transformer -> transformer(acc) }
                                return ByteBuffer.wrap(transformedBytes)
                            }
                        }
                    )) {
                        override fun findSource(name: String): ByteBuffer? =
                            ((packageMap[name.substring(0, name.lastIndexOf('.').let { if (it == -1) 0 else it })]
                                ?: listOf()) +
                                    (packageMap["*"] ?: listOf())).firstNotNullOfOrNull { it.findSource(name) }
                    },
                    resources = MutableResourceProvider(mutableListOf(ArchiveResourceProvider(classLoaderToArchive(delegate)))),
                    parent = ClassLoader.getPlatformClassLoader(),
                )
            )
        }

    override fun addTransformer(transform: (ByteArray) -> ByteArray) {
        transformers.add(transform)
    }

    override fun reTransform(name: String) {
        TODO("Re transformation not implemented yet.")
    }
}