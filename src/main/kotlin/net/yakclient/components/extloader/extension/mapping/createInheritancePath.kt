package net.yakclient.components.extloader.extension.mapping

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritancePath
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.mapClassName
import net.yakclient.archives.ArchiveReference
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

internal fun createInheritancePath(mappings: ArchiveMapping, entry: ArchiveReference.Entry, minecraft: ClassInheritanceTree) : ClassInheritancePath {
    val reader = ClassReader(entry.resource.open())
    val node = ClassNode()
    reader.accept(node, 0)

    fun getParent(name: String?) : ClassInheritancePath? {
        if (name == null) return null
        return minecraft[mappings.mapClassName(node.superName, MappingDirection.TO_FAKE)] ?: node.superName?.let { entry.handle.reader["$it.class"] }?.let { createInheritancePath(mappings, it, minecraft) }
    }

    return ClassInheritancePath(
            node.name,
            getParent(node.superName),
            node.interfaces.mapNotNull(::getParent)
    )
}