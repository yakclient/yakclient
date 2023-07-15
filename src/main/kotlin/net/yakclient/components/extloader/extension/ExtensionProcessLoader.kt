package net.yakclient.components.extloader.extension

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.mapClassName
import net.yakclient.archive.mapper.transform.mappingTransformConfigFor
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
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.extension.versioning.VersionedExtArchiveHandle
import org.objectweb.asm.ClassReader
import java.util.HashSet

public class ExtensionProcessLoader(
    private val privilegeManager: PrivilegeManager,
    private val parentClassloader: ClassLoader,
    private val mappings: ArchiveMapping,
    private val minecraftRef: ArchiveReference,
) : ProcessLoader<ExtensionInfo, ExtensionProcess> {
    private val config = mappingTransformConfigFor(
        mappings,
        MappingDirection.TO_FAKE
    )

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

        nonMixins
            .filter { it.name.endsWith(".class") }
            .forEach {
                val entry = it.transform(config, dependencies)
                archiveReference.writer.put(entry)
            }

        mixins.map {
            archiveReference.writer.put(transformMixinEntry(it, mixinClasses[it.name]!!, dependencies))
        }
    }

    private fun transformMixinEntry(
        entry: ArchiveReference.Entry,
        mixin: ExtensionMixin,
        dependencies: List<ArchiveTree>
    ): ArchiveReference.Entry {
        return entry.transform(
            TransformerConfig.of {
                transformClass {
                    it.name = mixin.destination.replace('.', '/')
                    config.ct(it)
                }
            },
            dependencies
        )
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
                        archives + minecraft,
                        privilegeManager,
                        parentClassloader,
                        containerHandle,
                    ),
                    Archives.Resolvers.ZIP_RESOLVER,
                    archives.toSet() + minecraft
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