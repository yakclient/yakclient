package dev.extframework.extension.core.util

import dev.extframework.archives.ArchiveHandle

private class THIS

public fun emptyArchiveHandle(): ArchiveHandle = object : ArchiveHandle {
    override val classloader: ClassLoader = THIS::class.java.classLoader
    override val name: String? = null
    override val packages: Set<String> = setOf()
    override val parents: Set<ArchiveHandle> = setOf()
}