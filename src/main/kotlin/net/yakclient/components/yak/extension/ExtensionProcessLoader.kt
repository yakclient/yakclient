package net.yakclient.components.yak.extension

import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.FieldIdentifier
import net.yakclient.archive.mapper.MappingType
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.transformArchive
import net.yakclient.archives.*
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.container.ProcessLoader
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.common.util.runCatching
import net.yakclient.components.yak.YakContext
import net.yakclient.components.yak.mapping.*
import org.objectweb.asm.Handle
import org.objectweb.asm.tree.*

public class ExtensionProcessLoader(
    private val privilegeManager: PrivilegeManager,
    private val parentClassloader: ClassLoader,
    private val context: ComponentContext,
    private val yakContext: YakContext,
    private val mappings: ArchiveMapping,
    private val minecraftRef: ArchiveReference,
    private val minecraftVersion: String
) : ProcessLoader<ExtensionInfo, ExtensionProcess> {
//    private val config = TransformerConfig.of {
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

//    private fun transformArchive(archiveReference: ArchiveReference, dependencies: List<ArchiveHandle>) {
//        archiveReference.reader.entries()
//            .filter { it.name.endsWith(".class") }
//            .forEach {
//                val entry = it.transform(config, dependencies + minecraftRef)
//                archiveReference.writer.put(entry)
//            }
//    }

    override fun load(info: ExtensionInfo): ExtensionProcess {
        val (ref, children, dependencies, metadata, containerHandle) = info

        val archives: List<ArchiveHandle> = children.map { it.process.archive } + dependencies + JpmArchives.moduleToArchive(this::class.java.module)

        transformArchive(ref, archives + minecraftRef, mappings, MappingDirection.TO_FAKE)

        val (erm) = metadata

        return ExtensionProcess(
            ExtensionReference { minecraft ->
                val result = Archives.resolve(
                    ref,
                    ExtensionClassLoader(
                        ref,
                        archives + minecraft,
                        privilegeManager,
                        parentClassloader,
                        containerHandle,
                        erm.versioningPartitions[minecraftVersion]?.toSet() ?: HashSet()
                    ),
                    Archives.Resolvers.JPM_RESOLVER,
                    archives.toSet() + minecraft
                )

                result.controller.addReads(result.module, minecraft.classloader.unnamedModule)

                val handle by result::archive

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