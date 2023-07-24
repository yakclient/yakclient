package net.yakclient.components.extloader.extension

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.boot.container.ProcessLoader
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.extension.versioning.VersionedExtArchiveHandle
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.tree.*
import java.util.HashSet

public class ExtensionProcessLoader(
    private val privilegeManager: PrivilegeManager,
    private val parentClassloader: ClassLoader,
    private val mappings: ArchiveMapping,
    private val minecraftRef: ArchiveReference,
) : ProcessLoader<ExtensionInfo, ExtensionProcess> {
    private val mcInheritanceTree = run {
        minecraftRef.reader.entries()
            .filterNot(ArchiveReference.Entry::isDirectory)
            .filter { it.name.endsWith(".class") }
            .map (::createRealMcInheritancePath)
            .associateBy { it.name }
    }

    private fun createRealMcInheritancePath(entry: ArchiveReference.Entry) : ClassInheritancePath {
        val classReader = ClassReader(entry.resource.open())
        val node = ClassNode()
        classReader.accept(node, 0)

        return ClassInheritancePath(
            mappings.mapClassName(node.name, MappingDirection.TO_REAL) ?: node.name,

            minecraftRef.reader[node.superName + ".class"]?.let(::createRealMcInheritancePath),
            node.interfaces?.mapNotNull { n ->
                minecraftRef. reader["$n.class"]?.let(::createRealMcInheritancePath)
            } ?: listOf()
        )
    }

    private fun createExtensionInheritanceTree(ref: ArchiveReference) : ClassInheritanceTree {
        val reader = ref.reader

        fun createExtensionInheritancePath(entry: ArchiveReference.Entry) : ClassInheritancePath {
            val classReader = ClassReader(entry.resource.open())
            val node = ClassNode()
            classReader.accept(node, 0)

            return ClassInheritancePath(
                node.name,
                reader[node.superName + ".class"]?.let(::createExtensionInheritancePath) ?: mcInheritanceTree[node.superName],
                node.interfaces?.mapNotNull { n ->
                     reader["$n.class"]?.let(::createExtensionInheritancePath) ?: mcInheritanceTree[n]
                } ?: listOf()
            )
        }

        return ref.reader.entries()
            .filterNot(ArchiveReference.Entry::isDirectory)
            .filter { it.name.endsWith(".class") }
            .map (::createExtensionInheritancePath)
            .associateBy { it.name }
    }

    private fun mixinConfigFor(destination: String, tree: ClassInheritanceTree) = TransformerConfig.of {
        fun ClassInheritancePath.toCheck(): List<String> {
            return listOf(name) + interfaces.flatMap { it.toCheck() } + (superClass?.toCheck() ?: listOf())
        }

        transformClass { classNode: ClassNode ->
            val destinationInternal = destination.replace('.', '/')
            mappings.run {
                val direction = MappingDirection.TO_FAKE
                classNode.methods.forEach { methodNode ->
                    // AbstractInsnNode
                    methodNode.instructions.forEach { insnNode ->
                        when (insnNode) {
                            is FieldInsnNode -> {
                                val newOwner =
                                    if (insnNode.owner == classNode.name) destinationInternal else insnNode.owner

                                insnNode.name = tree[newOwner]?.toCheck()?.firstNotNullOfOrNull {
                                    mapFieldName(
                                        it,
                                        insnNode.name,
                                        direction
                                    )
                                } ?: insnNode.name

                                insnNode.owner = mapClassName(newOwner, direction) ?: newOwner
                                insnNode.desc = mapType(insnNode.desc, direction)
                            }

                            is InvokeDynamicInsnNode -> {
                                val newOwner = if (insnNode.bsm.owner == classNode.name) destinationInternal else insnNode.bsm.owner

                                fun Handle.mapHandle(): Handle = Handle(
                                    tag,
                                    mapType(newOwner, direction),
                                    tree[newOwner]?.toCheck()?.firstNotNullOfOrNull {
                                        mapMethodName(
                                            it,
                                            name,
                                            desc,
                                            direction
                                        )
                                    } ?: name,
                                    mapMethodDesc(desc, direction),
                                    isInterface
                                )

                                // Type and Handle
                                insnNode.bsm = insnNode.bsm.mapHandle()


                                // Can ignore name because only the name of the bootstrap method is known at compile time and that is held in the handle field
                                insnNode.desc =
                                    mapMethodDesc(
                                        insnNode.desc,
                                        direction
                                    ) // Expected descriptor type of the generated call site
                            }

                            is MethodInsnNode -> {
                                val newOwner =
                                    if (insnNode.owner == classNode.name) destinationInternal else insnNode.owner

                                insnNode.name = tree[newOwner]?.toCheck()?.firstNotNullOfOrNull {
                                    mapMethodName(
                                        it,
                                        insnNode.name,
                                        insnNode.desc,
                                        direction
                                    )
                                } ?: insnNode.name

                                insnNode.owner = mapClassName(newOwner, direction) ?: newOwner
                                insnNode.desc = mapMethodDesc(insnNode.desc, direction)
                            }

                            is MultiANewArrayInsnNode -> {
                                insnNode.desc = mapType(insnNode.desc, direction)
                            }

                            is TypeInsnNode -> {
                                insnNode.desc = mapClassName(insnNode.desc, direction) ?: insnNode.desc
                            }
                        }
                    }
                }
            }
        }
    }

    private fun transformEachEntry(
        erm: ExtensionRuntimeModel,
        archiveReference: ArchiveReference,
        dependencies: List<ArchiveTree>
    ) {
        val mixinClasses = erm.versionPartitions
            .flatMapTo(HashSet()) { v ->
                v.mixins.map { ("${v.path.removeSuffix("/")}/${it.classname.replace('.', '/')}.class") to it }
            }.associate { it }

        val (mixins, nonMixins) = archiveReference.reader.entries()
            .filterNot(ArchiveReference.Entry::isDirectory)
            .partition { mixinClasses.contains(it.name) }

        val inheritanceTree = createExtensionInheritanceTree(archiveReference)
        val config = mappingTransformConfigFor(
            mappings,
            MappingDirection.TO_FAKE,
            inheritanceTree + mcInheritanceTree
        )

        nonMixins
            .filter { it.name.endsWith(".class") }
            .forEach {
                val entry = it.transform(config, dependencies)
                archiveReference.writer.put(entry)
            }

        mixins.map {
            val new = it.transform(
                mixinConfigFor(mixinClasses[it.name]!!.destination, mcInheritanceTree),
                dependencies
            )
            archiveReference.writer.put(new)
        }
    }

    override fun load(info: ExtensionInfo): ExtensionProcess {
        val (ref, children, dependencies, erm, containerHandle) = info

        val archives: List<ArchiveHandle> =
            children.map { it.process.archive } + dependencies

        transformEachEntry(erm, ref, archives + minecraftRef)

        return ExtensionProcess(
            ExtensionReference { minecraft ->
                val result = Archives.resolve(
                    ref.delegate,
                    ExtensionClassLoader(
                        ref,
                        archives,
                        privilegeManager,
                        parentClassloader,
                        containerHandle,minecraft
                    ),
                    Archives.Resolvers.ZIP_RESOLVER,
                    archives.toSet()
                )

                val handle = VersionedExtArchiveHandle(result.archive, ref)

                val s = "${erm.groupId}:${erm.name}:${erm.version}"

                val extensionClass =
                    runCatching(ClassNotFoundException::class) { handle.classloader.loadClass(erm.extensionClass) }
                        ?: throw IllegalArgumentException("Could not load extension: '$s' because the class: '${erm.extensionClass}' couldnt be found.")
                val extensionConstructor = runCatching(NoSuchMethodException::class) { extensionClass.getConstructor() }
                    ?: throw IllegalArgumentException("Could not find no-arg constructor in class: '${erm.extensionClass}' in extension: '$s'.")

                val instance = extensionConstructor.newInstance() as? Extension
                    ?: throw IllegalArgumentException("Extension class: '${erm.extensionClass}' does not implement: '${Extension::class.qualifiedName} in extension: '$s'.")

                instance to handle
            },
            ExtensionContext()
        )
    }
}