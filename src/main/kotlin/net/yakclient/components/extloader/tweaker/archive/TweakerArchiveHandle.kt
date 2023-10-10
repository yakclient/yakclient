package net.yakclient.components.extloader.tweaker.archive

import kotlinx.coroutines.delay
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference

//class TweakerArchiveHandle : ArchiveH {
//}

internal fun TweakerArchiveHandle(
    name: String,
    classLoader: ClassLoader,
    archiveReference: TweakerArchiveReference,
    parents: Set<ArchiveHandle>
): ArchiveHandle = object : ArchiveHandle {
    override val classloader: ClassLoader = classLoader
    override val name: String = name
    override val packages: Set<String> = run {
        archiveReference.reader.entries()
            .filter { it.name.endsWith(".class") && !it.name.endsWith("module-info.class") }
            .mapTo(HashSet()) {
                it.name
                    .removePrefix(archiveReference.path).removePrefix("/")
                    .split("/")
                    .let { it.subList(0, it.lastIndex) }
                    .joinToString(separator = ".")
            }
    }
    override val parents: Set<ArchiveHandle> = parents

}