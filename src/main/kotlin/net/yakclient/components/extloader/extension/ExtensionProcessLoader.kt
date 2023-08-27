package net.yakclient.components.extloader.extension

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.AwareClassWriter
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.boot.container.ProcessLoader
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.common.util.LazyMap
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.extension.versioning.VersionedExtArchiveHandle
import net.yakclient.internal.api.InternalRegistry
import net.yakclient.internal.api.extension.ExtensionRuntimeModel
import net.yakclient.internal.api.extension.archive.ExtensionArchiveReference
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.tree.*

public class ExtensionProcessLoader(
        private val privilegeManager: PrivilegeManager,
        private val parentClassloader: ClassLoader,
        private val minecraftRef: ArchiveReference,
        private val mcVersion: String
) : ProcessLoader<ExtensionInfo, ExtensionProcess> {
    private val mcInheritanceTree = createFakeInheritanceTree(minecraftRef.reader)

    private fun transformEachEntry(
            erm: ExtensionRuntimeModel,
            archiveReference: ExtensionArchiveReference,
            dependencies: List<ArchiveTree>
    ): ClassInheritanceTree {
        // Gets all the loaded mixins and map them to their actual location in the archive reference.
        val mixinClasses = erm.versionPartitions
                .flatMapTo(HashSet()) { v ->
                    v.mixins.map { ("${v.path.removeSuffix("/")}/${it.classname.replace('.', '/')}.class") }
                }

        // Goes through the enabled partitions and main, and group them by same mapping types. Then load that specific mapper
        val entryToMapping = (archiveReference.enabledPartitions + archiveReference.mainPartition)
                .map {
                    it to archiveReference.reader.entriesIn(it.name)
                            .filterNot(ArchiveReference.Entry::isDirectory)
                            .filter { entry -> entry.name.endsWith(".class") }
                }
                .groupBy { it.first.mappings }
                .mapValues { (_, entries) -> entries.flatMap { it.second } }
                .mapValues { (_, entries) ->
                    entries.filterNot {mixinClasses.contains(it.name) }
                }.flatMap { (mapping, entries) ->
                    val mappings = (InternalRegistry.extensionMappingContainer.get(mapping.type)?.forIdentifier(mcVersion)
                            ?: throw IllegalArgumentException("Failed to find mapping type: '${mapping.type}', options are: '${InternalRegistry.extensionMappingContainer.objects().keys}"))
                    entries.map { it to mappings }
                }.toMap()


        fun inheritancePathFor(
                entry: ArchiveReference.Entry
        ): ClassInheritancePath {
            val reader = ClassReader(entry.resource.open())
            val node = ClassNode()
            reader.accept(node, 0)

            val mappings = entryToMapping[entry] ?: throw IllegalArgumentException(
                    "Unknown class: '${entry.name}' encountered when trying to create inheritance path " +
                            "for extension archive mapping. This might signify that you either have a illegal " +
                            "dependency on a partition that is not active when partition: '${archiveReference.reader.determinePartition(entry)}' " +
                            "is, or this class is a subtype of a mixin class (also illegal).")

            fun getParent(name: String?): ClassInheritancePath? {
                if (name == null) return null
                return mcInheritanceTree[mappings.mapClassName(node.superName, MappingDirection.TO_FAKE)]
                        ?: node.superName?.let { entry.handle.reader["$it.class"] }?.let { inheritancePathFor(it) }
            }

            return ClassInheritancePath(
                    node.name,
                    getParent(node.superName),
                    node.interfaces.mapNotNull(::getParent)
            )
        }

        // Load an inheritance tree based on the mappings of each partition
        val treeInternal = entryToMapping.map { (e, _) ->
            inheritancePathFor(e)
        }.associateBy { it.name }
        val tree = object : Map<String, ClassInheritancePath> by treeInternal {
            override fun get(key: String): ClassInheritancePath? {
                return treeInternal[key] ?: mcInheritanceTree[key]
            }
        }

        // Map each entry based on its respective mapper
        entryToMapping.forEach { (entry, mappings) ->
            val config = mapperFor(mappings, tree)

            Archives.resolve(
                    ClassReader(entry.resource.open()),
                    config,
            )

            archiveReference.writer.put(entry.transform(
                    config, dependencies
            ))
        }

        // Not particularly good design here, but its all internal class stuff that can be very easily moved around without disrupting public apis so its good enough for now
        return tree
    }

    override fun load(info: ExtensionInfo): ExtensionProcess {
        val (ref, children, dependencies, erm, containerHandle) = info

        val archives: List<ArchiveHandle> =
                children.map { it.process.archive } + dependencies

        val tree = transformEachEntry(erm, ref, archives + minecraftRef)

        return ExtensionProcess(
                ExtensionReference(ref, tree, mcVersion) { minecraft ->
                    val result = Archives.resolve(
                            ref.delegate,
                            ExtensionClassLoader(
                                    ref,
                                    archives,
                                    privilegeManager,
                                    parentClassloader,
                                    containerHandle, minecraft
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

    private fun mapperFor(
            mappings: ArchiveMapping,
            tree: ClassInheritanceTree,
    ): TransformerConfig {
        val direction = MappingDirection.TO_FAKE

        fun ClassInheritancePath.fromTreeInternal(): ClassInheritancePath {
            val mappedName = mappings.mapClassName(name, MappingDirection.TO_REAL) ?: name

            return ClassInheritancePath(
                    mappedName,
                    superClass?.fromTreeInternal(),
                    interfaces.map { it.fromTreeInternal() }
            )
        }

        val lazilyMappedTree = LazyMap<String, ClassInheritancePath> {
            tree[mappings.mapClassName(it, MappingDirection.TO_FAKE)]?.fromTreeInternal()
        }

        return mappingTransformConfigFor(
                mappings,
                direction,
                lazilyMappedTree
        )
//        return TransformerConfig.of {
//            transformClass { classNode: ClassNode ->
//                mappings.run {
//                    for (methodNode in classNode.methods) {
//                        methodNode.desc = mapMethodDesc(methodNode.desc, direction)
//
//                        if (methodNode.signature != null)
//                            methodNode.signature = mapAnySignature(methodNode.signature, direction)
//
//                        methodNode.exceptions = methodNode.exceptions.map { mapType(it, direction) }
//
//                        methodNode.localVariables?.forEach {
//                            it.desc = mapType(it.desc, direction)
//                        }
//
//                        methodNode.tryCatchBlocks.forEach {
//                            if (it.type != null) it.type = mapClassName(it.type, direction) ?: it.type
//                        }
//
//                        methodNode.instructions.forEach { insnNode ->
//                            when (insnNode) {
//                                is FieldInsnNode -> {
//
//                                    insnNode.name = fromTree(insnNode.owner) {
//                                        mapFieldName(
//                                                it,
//                                                insnNode.name,
//                                                direction
//                                        )
//                                    } ?: insnNode.name
//
//                                    insnNode.owner = mapClassName(insnNode.owner, direction) ?: insnNode.owner
//                                    insnNode.desc = mapType(insnNode.desc, direction)
//                                }
//
//                                is InvokeDynamicInsnNode -> {
//                                    fun Handle.mapHandle(): Handle = Handle(
//                                            tag,
//                                            mapClassName(insnNode.bsm.owner, direction) ?: insnNode.bsm.owner,
//                                            fromTree(insnNode.bsm.owner) {
//                                                mapMethodName(
//                                                        it,
//                                                        name,
//                                                        desc,
//                                                        direction
//                                                )
//                                            } ?: name,
//                                            mapMethodDesc(desc, direction),
//                                            isInterface
//                                    )
//
//                                    // Type and Handle
//                                    insnNode.bsm = insnNode.bsm.mapHandle()
//
//
//                                    // Can ignore name because only the name of the bootstrap method is known at compile time and that is held in the handle field
//                                    insnNode.desc =
//                                            mapMethodDesc(
//                                                    insnNode.desc,
//                                                    direction
//                                            ) // Expected descriptor type of the generated call site
//                                }
//
//                                is MethodInsnNode -> {
//                                    insnNode.name = fromTree(insnNode.owner) {
//                                        mapMethodName(
//                                                it,
//                                                insnNode.name,
//                                                insnNode.desc,
//                                                direction
//                                        )
//                                    } ?: insnNode.name
//
//                                    insnNode.owner = mapClassName(insnNode.owner, direction) ?: insnNode.owner
//                                    insnNode.desc = mapMethodDesc(insnNode.desc, direction)
//                                }
//
//                                is MultiANewArrayInsnNode -> {
//                                    insnNode.desc = mapType(insnNode.desc, direction)
//                                }
//
//                                is TypeInsnNode -> {
//                                    insnNode.desc = mapClassName(insnNode.desc, direction) ?: insnNode.desc
//                                }
//                            }
//                        }
//
//                        println("HEy")
//                    }
//                    classNode.interfaces = classNode.interfaces.map { mapClassName(it, direction) ?: it }
//
//                    classNode.superName = mapClassName(classNode.superName, direction) ?: classNode.superName
//                }
//            }
//        }
    }
}