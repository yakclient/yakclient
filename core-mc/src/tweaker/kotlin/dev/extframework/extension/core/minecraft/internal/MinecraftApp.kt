package dev.extframework.extension.core.minecraft.internal

import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.findShortest
import dev.extframework.archive.mapper.newMappingsGraph
import dev.extframework.archive.mapper.transform.ClassInheritancePath
import dev.extframework.archive.mapper.transform.ClassInheritanceTree
import dev.extframework.archive.mapper.transform.mapClassName
import dev.extframework.archive.mapper.transform.mappingTransformConfigFor
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.Archives
import dev.extframework.archives.transform.AwareClassWriter
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.common.util.*
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.mappingTargetAttrKey
import dev.extframework.extension.core.util.withSlashes
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.environment.wrkDirAttrKey
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

internal class MinecraftApp(
    private val appDelegate: ApplicationTarget,
    private val environment: ExtensionEnvironment
) : ApplicationTarget by appDelegate {
    private val path: Path =
        environment[wrkDirAttrKey].extract().value resolve "minecraft" resolve appDelegate.node.descriptor.version resolve "classes"
    private val mappings: ArchiveMapping by lazy {
        newMappingsGraph(environment[mappingProvidersAttrKey].extract())
            .findShortest(source, destination)
            .forIdentifier(appDelegate.node.descriptor.version)
    }
    private val source: String = MojangMappingProvider.OBF_TYPE
    private val destination: String by lazy {
        environment[mappingTargetAttrKey].extract().value
    }

    private fun getMappedClassFile(
        internalName: String,
        initial: URL,
    ): URL {
        val path = path resolve ("$internalName.class")

        if (path.exists()) return path.toUrl()

        fun InputStream.classNode(parsingOptions: Int = 0): ClassNode {
            val node = ClassNode()
            ClassReader(this).accept(node, parsingOptions)
            return node
        }

        fun obfuscatedPathFor(
            name: String
        ): ClassInheritancePath {
            val node =
                appDelegate.node.handle?.classloader?.getResource(name.withSlashes() + ".class")?.openStream()
                    ?.classNode()
                    ?: return ClassInheritancePath(name, null, listOf())

            return ClassInheritancePath(
                node.name,
                node.superName?.let(::obfuscatedPathFor),
                node.interfaces?.mapNotNull { n ->
                    obfuscatedPathFor(n)
                } ?: listOf()
            )
        }


        val obfInheritanceTree: ClassInheritanceTree = LazyMap(lazyImpl = ::obfuscatedPathFor)

        val config = mappingTransformConfigFor(
            mappings, source, destination, obfInheritanceTree
        )

        val bytes = Archives.resolve(
            ClassReader(initial.openStream()),
            config,
            writer = object : AwareClassWriter(
                listOf(),
                Archives.WRITER_FLAGS
            ) {
                override fun loadType(name: String): HierarchyNode {
                    val node = appDelegate.node.handle?.classloader?.getResource(
                        // Map back to source then query
                        (mappings.mapClassName(name, destination, source) ?: name) + ".class"
                    )?.openStream()?.classNode() ?: return super.loadType(name)

                    return UnloadedClassNode(
                        node,
                    )
                }
            }
        )

        path.make()
        path.writeBytes(bytes)

        return path.toUrl()
    }

    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> by appDelegate.node {
            val delegateClasses = access.targets
                .map { it.relationship.node }
                .filterIsInstance<ClassLoadedArchiveNode<*>>()

            private val delegateResources = ArchiveResourceProvider(appDelegate.node.handle)

            private val resourceProvider = object : ResourceProvider {
                override fun findResources(name: String): Sequence<URL> {
                    return if (name.endsWith(".class")) {
                        val internalName = name.removeSuffix(".class")
                        val obfuscatedName = mappings.mapClassName(internalName, destination, source) ?: internalName

                        delegateResources.findResources("$obfuscatedName.class").map {
                            getMappedClassFile(internalName, it)
                        }
                    } else delegateResources.findResources(name)
                }
            }

            override val handle: ArchiveHandle = classLoaderToArchive(
                IntegratedLoader(
                    name = "Minecraft @ ${appDelegate.node.handle?.classloader?.name ?: "app"}",
                    classProvider = DelegatingClassProvider(delegateClasses.map { ArchiveClassProvider(it.handle) }),
                    resourceProvider = resourceProvider,
                    sourceProvider = object : SourceProvider {
                        override val packages: Set<String> = setOf("*")

                        override fun findSource(name: String): ByteBuffer? {
                            return resourceProvider.findResources(name.withSlashes() + ".class")
                                .firstOrNull()
                                ?.openStream()
                                ?.readInputStream()
                                ?.let(ByteBuffer::wrap)
                        }
                    },
                    parent = ClassLoader.getSystemClassLoader(),
                )
            )
        }
}