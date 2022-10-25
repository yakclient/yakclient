package net.yakclient.plugins.yakclient.extension

import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveResolver
import net.yakclient.archives.Archives
import net.yakclient.boot.container.ProcessLoader
import net.yakclient.boot.security.PrivilegeManager

public class ExtensionProcessLoader(
    private val privilegeManager: PrivilegeManager,
    private val parentClassloader: ClassLoader,
    private val resolver: ArchiveResolver<*, *>,
) : ProcessLoader<ExtensionInfo, ExtensionProcess> {
    override fun load(info: ExtensionInfo): ExtensionProcess {
        val (ref, children, dependencies, erm) = info

        val archives = children.map { it.handle } + dependencies
        val handle = Archives.resolve(
            ref,
            ExtensionClassLoader(
                ref,
                archives,
                privilegeManager,
                parentClassloader
            ),
            resolver as ArchiveResolver<ArchiveReference, *>,
            archives.toSet()
        ).archive

        val s = "${erm.groupId}:${erm.name}:${erm.version}"

        val extensionClass = runCatching { handle.classloader.loadClass(erm.extensionClass) }.getOrNull()
            ?: throw IllegalArgumentException("Could not load extension: '$s' because the class: '${erm.extensionClass}' couldnt be found.")
        val extensionConstructor = runCatching { extensionClass.getConstructor() }.getOrNull()
            ?: throw IllegalArgumentException("Could not find no-arg constructor in class: '${erm.extensionClass}' in extension: '$s'.")

        val instance = extensionConstructor.newInstance() as? Extension
            ?: throw IllegalArgumentException("Extension class: '${erm.extensionClass}' does not implement: '${Extension::class.qualifiedName} in extension: '$s'.")

        return ExtensionProcess(
            instance,
            handle,
        )
    }
}