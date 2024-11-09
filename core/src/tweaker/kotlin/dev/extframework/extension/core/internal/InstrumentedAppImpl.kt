package dev.extframework.extension.core.internal

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.transform.AwareClassWriter
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.common.util.runCatching
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
import java.nio.ByteBuffer
import java.nio.file.Path

public class InstrumentedAppImpl(
    override val delegate: ApplicationTarget,
    private val linker: TargetLinker,
    override val agents: MutableObjectSetAttribute<MixinAgent>
) : InstrumentedApplicationTarget {
    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
//            TODO do mixins in resources?
//            private val resourceProvider = object : ResourceProvider {
//                private val resourceDelegate = ArchiveResourceProvider(delegate.node.handle)
//                override fun findResources(name: String): Sequence<URL> {
//                    TODO("Not yet implemented")
//                }
//            }

            override val access: ArchiveAccessTree = delegate.node.access
            override val descriptor: ApplicationDescriptor = delegate.node.descriptor
            override val handle: ArchiveHandle = classLoaderToArchive(IntegratedLoader(
                name = "Mixins @ app",
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

                    override fun findSource(name: String): ByteBuffer? {
                        val reader = delegate.node.handle!!.getResource(name.withSlashes() + ".class")
                            ?.let { resource -> ClassReader(resource) }
                        val node = reader?.let {
                            val node = ClassNode()
                            reader.accept(node, 0)
                            node
                        }

                        // TODO merging
                        val transformedNode = agents.fold(node) { acc, it ->
                            it.transformClass(name, acc)
                        } ?: return null

                        val writer = object : AwareClassWriter(
                            listOf(),
                            COMPUTE_FRAMES,
                            reader
                        ) {
                            override fun loadType(name: String): HierarchyNode {
                                val resourceName = "$name.class"
                                val resource = this@InstrumentedAppImpl.delegate.node.handle?.classloader?.getResource(
                                    resourceName
                                ) ?: linker.extensionLoader.getResource(
                                    resourceName
                                )

                                return resource?.openStream()?.classNode()
                                    ?.let(::UnloadedClassNode)
                                    ?: runCatching(ClassNotFoundException::class) {
                                        LoadedClassNode(Class.forName(name.replace('/', '.')))
                                    } ?: run {
                                        System.err.println("Recomputing stack frames and asked for super type information about class: '$name'. This class could not be loaded so a stub (inheriting from java/lang/Object) was returned.")
                                        // This is confusing. The only really that we do this is because it's better to return a type than not.
                                        // Often a class not being found in this stage is more an issue with the app we are targeting than anything
                                        // the user can control. We set isInterface to true so that java/lang/Object is returned.
                                        object : HierarchyNode {
                                            override val interfaceNodes: List<HierarchyNode> = listOf()
                                            override val isInterface: Boolean = true
                                            override val name: String = name
                                            override val superNode: HierarchyNode? = null
                                        }
                                    }
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
    override val path: Path by delegate::path

    override fun registerAgent(agent: MixinAgent) {
        agents.add(agent)
    }

    override fun redefine(name: String) {
        TODO("Not yet implemented")
    }
}