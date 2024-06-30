package dev.extframework.components.extloader.util

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

public fun java.io.InputStream.parseNode(): ClassNode {
    val node = ClassNode()
    ClassReader(this).accept(node, 0)
    return node
}