package net.yakclient.components.yak.extension

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.mappingTransformConfigFor
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.boot.container.ProcessLoader
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.common.util.runCatching
import net.yakclient.components.yak.extension.versioning.VersionedExtArchiveHandle

public class ExtensionProcessLoader(
    private val privilegeManager: PrivilegeManager,
    private val parentClassloader: ClassLoader,
    mappings: ArchiveMapping,
    private val minecraftRef: ArchiveReference,
) : ProcessLoader<ExtensionInfo, ExtensionProcess> {
    private val config = mappingTransformConfigFor(
        mappings,
        MappingDirection.TO_FAKE
    )
//        transformField { node ->
//            node.desc = mappings.mapType(node.desc)
//
//            node
//        }
//
//        transformMethod { node ->
//            mappings.run {
//                node.desc = mapMethodDesc(node.desc)
//
//                node.exceptions = node.exceptions.map(::mapType)
//
//                node.localVariables.forEach {
//                    it.desc = mapType(it.desc)
//                }
//
//                node.tryCatchBlocks.forEach {
//                    it.type = mapClassName(it.type)
//                }
//
//                // AbstractInsnNode
//                node.instructions.forEach {
//                    when (it) {
//                        is FieldInsnNode -> {
//                            val mapClassName = mapClassName(it.owner)
//                            it.name = run {
//                                getMappedClass(it.owner)
//                                    ?.fields
//                                    ?.get(FieldIdentifier(
//                                        it.name,
//                                        MappingType.REAL
//                                    ))
//                                    ?.fakeIdentifier?.name
//                                    ?: it.name
//                            }
//                            it.owner = mapClassName
//                            it.desc = mapType(it.desc)
//                        }
//                        is InvokeDynamicInsnNode -> {
//                            // Can ignore name because only the name of the bootstrap method is known at compile time and that is held in the handle field
//                            it.desc = mapMethodDesc(it.desc) // Expected descriptor type of the generated call site
//
//                            val desc = mapMethodDesc(it.bsm.desc)
//                            it.bsm = Handle(
//                                it.bsm.tag,
//                                mapType(it.bsm.owner),
//                                mapMethodName(it.bsm.owner, it.bsm.name, desc),
//                                desc,
//                                it.bsm.isInterface
//                            )
//                        }
//                        is MethodInsnNode -> {
//                            val mapDesc = mapMethodDesc(it.desc)
//
//                            it.name = mapMethodName(it.owner, it.name, mapDesc)
//                            it.owner = mapClassName(it.owner)
//                            it.desc = mapDesc
//                        }
//                        is MultiANewArrayInsnNode -> {
//                            it.desc = mapType(it.desc)
//                        }
//                        is TypeInsnNode -> {
//                            it.desc = mapClassName(it.desc)
//                        }
//                    }
//                }
//            }
//
//            node
//        }
//    }

    private fun transformEachEntry(archiveReference: ArchiveReference, dependencies: List<ArchiveTree>) {
        archiveReference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .forEach {
                val entry = it.transform(config, dependencies)
                archiveReference.writer.put(entry)
            }
    }

    override fun load(info: ExtensionInfo): ExtensionProcess {
        val (ref, children, dependencies, erm, containerHandle) = info

        val archives: List<ArchiveHandle> =
            children.map { it.process.archive } + dependencies

        transformEachEntry(ref, archives + minecraftRef)

        return ExtensionProcess(
            ExtensionReference { minecraft ->
                val result = Archives.resolve(
                    ref.delegate,
                    ExtensionClassLoader(
                        ref,
                        archives + minecraft,
                        privilegeManager,
                        parentClassloader,
                        containerHandle,
                    ),
                    Archives.Resolvers.ZIP_RESOLVER,
                    archives.toSet() + minecraft
                )

//                result.controller.addReads(result.module, minecraft.classloader.unnamedModule)

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