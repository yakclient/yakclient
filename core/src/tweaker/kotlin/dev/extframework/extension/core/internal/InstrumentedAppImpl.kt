package dev.extframework.extension.core.internal

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.transform.AwareClassWriter
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.extension.core.mixin.MixinAgent
import dev.extframework.extension.core.target.InstrumentedApplicationTarget
import dev.extframework.extension.core.target.TargetLinker
import dev.extframework.extension.core.util.withSlashes
import dev.extframework.internal.api.environment.MutableObjectSetAttribute
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer

public class InstrumentedAppImpl(
    override val delegate: ApplicationTarget,
    private val linker: TargetLinker,
    override val agents: MutableObjectSetAttribute<MixinAgent>
) : InstrumentedApplicationTarget {
    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
            // TODO do mixins in resources?
            private val resourceProvider = object : ResourceProvider {
                private val resourceDelegate = ArchiveResourceProvider(delegate.node.handle)
                override fun findResources(name: String): Sequence<URL> {
                    TODO("Not yet implemented")
                }
            }

            override val access: ArchiveAccessTree = delegate.node.access
            override val descriptor: ApplicationDescriptor = delegate.node.descriptor
            override val handle: ArchiveHandle = classLoaderToArchive(IntegratedLoader(
                name = "Mixins @ ${delegate.node.handle?.classloader?.name ?: "app"}",
                classProvider = DelegatingClassProvider(access.targets
                    .map { it.relationship.node }
                    .filterIsInstance<ClassLoadedArchiveNode<*>>()
                    .map { ArchiveClassProvider(it.handle) }),
                resourceProvider = ArchiveResourceProvider(delegate.node.handle),
                sourceProvider = object : SourceProvider {
                    override val packages: Set<String> = setOf("*")

                    private fun InputStream.classNode(parsingOptions: Int = 0): ClassNode {
                        val node = ClassNode()
                        ClassReader(this).accept(node, parsingOptions)
                        return node
                    }

                    // TODO merging
                    override fun findSource(name: String): ByteBuffer? {
                        val reader = delegate.node.handle!!.getResource(name.withSlashes() + ".class")
                            ?.let { resource -> ClassReader(resource) }
                        val node = reader?.let {
                            val node = ClassNode()
                            reader.accept(node, 0)
                            node
                        }

                        val transformedNode = agents.fold(node) { acc, it ->
                            it.transformClass(name, acc)
                        } ?: return null

                        //AwareClassWriter(
                        //                            listOf(object : ArchiveTree {
                        //                                override fun getResource(name: String): InputStream? {
                        //                                    return findSource(name.withDots().removeSuffix(".class"))?.toBytes()
                        //                                        ?.let(::ByteArrayInputStream)
                        //                                }
                        //                            }, classLoaderToArchive(linker.extensionLoader)),
                        //                            ClassWriter.COMPUTE_FRAMES,
                        //                            reader
                        //                        )

                        val writer = object : AwareClassWriter(
                            listOf(),
                            COMPUTE_FRAMES,
                            reader
                        ) {
                            override fun loadType(name: String): HierarchyNode {
                                return UnloadedClassNode(
//                                    name.takeUnless { it == currentName }?.let {
//                                        findSource(it)
//                                    }?.let {
//                                        ByteArrayInputStream(it.toBytes()).classNode()
//                                    } ?:
                                    this@InstrumentedAppImpl.delegate.node.handle?.classloader?.getResource(
                                        "$name.class"
                                    )?.openStream()?.classNode() ?: return super.loadType(name),
                                )
                            }
                        }

                        transformedNode.accept(writer)

                        return ByteBuffer.wrap(writer.toByteArray())
                    }
                },
                parent = linker.extensionLoader
            )
            )
        }

    override fun registerAgent(agent: MixinAgent) {
        agents.add(agent)
    }

    override fun redefine(name: String) {
        TODO("Not yet implemented")
    }
}