package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveAccessTree
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
import net.yakclient.components.extloader.target.TargetLinker
import java.net.URL
import java.nio.ByteBuffer

public open class ExtensionClassLoader(
    archive: ExtensionArchiveReference,
//    dependencies: List<ArchiveHandle>,
    accessTree: ArchiveAccessTree,
    manager: PrivilegeManager,
    parent: ClassLoader,
//    handle: ContainerHandle<ExtensionProcess>,
//    linker: TargetLinker
) : IntegratedLoader(
    name = "Extension ${archive.erm.name}",
    classProvider = DelegatingClassProvider(
        accessTree.targets
            .map { it.relationship.classes }
    ),
    sourceProvider = ExtensionSourceProvider(archive),
    resourceProvider = ArchiveResourceProvider(archive),
//        val provider = ExtensionSourceProvider(archive)
////        private val mixinClasses = archive.enabledPartitions
////            .flatMap(ExtensionVersionPartition::mixins)
////            .mapTo(HashSet(), ExtensionMixin::classname)
//
//        override val packages: Set<String> by provider::packages
//        override fun getResource(name: String): URL? {
//            return provider.getResource(name)
//        }
//
//        override fun getResource(name: String, module: String): URL? {
//            return provider.getResource(name, module)
//        }
//
//        override fun getSource(name: String): ByteBuffer? {
////            return if (mixinClasses.contains(name)) throw IllegalArgumentException(
////                """
////An attempt to load the following mixin class: '$name' has been made. This is an illegal operation. If you have static util methods in
////your mixin class or something else you wish to access, please refactor it into a separate class that is not a mixin. This feature is
////illegal due to how minecraft obfuscation and extension mapping work together, if you have further questions please get in touch with
////YakClient developers.
////                """.trimIndent()
////            )
////            else
//            return provider.getSource(name)
//        }
//    },
//    sourceDefiner = { name, bytes, loader, definer ->
//        val domain = ProtectionDomain(ContainerSource(handle), manager.permissions)
//
//        definer(name, bytes, domain)
//    },
    parent = parent
) {
    override fun loadClass(name: String): Class<*> {
        return runCatching(ClassNotFoundException::class) {
            super.loadClass(name)
        } ?: throw ClassNotFoundException(name)
    }
}
