package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.container.ContainerSource
import net.yakclient.boot.loader.*
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import java.security.ProtectionDomain
import net.yakclient.components.extloader.extension.versioning.ExtensionSourceProvider
import net.yakclient.components.extloader.api.extension.ExtensionMixin
import net.yakclient.components.extloader.api.extension.ExtensionVersionPartition
import java.nio.ByteBuffer

public fun ExtensionClassLoader(
    archive: ExtensionArchiveReference,
    dependencies: List<ArchiveHandle>,
    manager: PrivilegeManager,
    parent: ClassLoader,
    handle: ContainerHandle<ExtensionProcess>,
    linker: MinecraftLinker
): ClassLoader {
    val mixinClasses = archive.enabledPartitions
        .flatMap(ExtensionVersionPartition::mixins)
        .mapTo(HashSet(), ExtensionMixin::classname)

    val extensionSourceProvider = ExtensionSourceProvider(archive)
    return object : IntegratedLoader(
        cp = DelegatingClassProvider(
            dependencies.map(::ArchiveClassProvider) + linker.minecraftClassProvider
        ),
        sp = object : SourceProvider by extensionSourceProvider {
            override fun getSource(name: String): ByteBuffer? {
                return if (mixinClasses.contains(name)) null
                else extensionSourceProvider.getSource(name)
            }
        },
        sd = { name, bytes, loader, definer ->
            val domain = ProtectionDomain(ContainerSource(handle), manager.permissions)

            definer(name, bytes, domain)
        },
        parent = parent
    ) {
        override fun loadClass(name: String): Class<*> {
            return runCatching(ClassNotFoundException::class) { super.loadClass(name) }
                ?: if (mixinClasses.contains(name)) throw IllegalArgumentException(
                    """
An attempt to load the following mixin class: '$name' has been made. This is an illegal operation. If you have static util methods in 
your mixin class or something else you wish to access, please refactor it into a separate class that is not a mixin. This feature is 
illegal due to how minecraft obfuscation and extension mapping work together, if you have further questions please get in touch with 
YakClient developers.
                """.trimIndent()
                )
                else throw ClassNotFoundException(name)
        }
    }
}