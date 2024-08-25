package dev.extframework.internal.api.target

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey

public typealias ApplicationDescriptor = SimpleMavenDescriptor

public interface ApplicationTarget : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ApplicationTarget

    public val node: ClassLoadedArchiveNode<ApplicationDescriptor>

    public companion object : EnvironmentAttributeKey<ApplicationTarget>
}

//public inline fun ApplicationTarget.addTransformer(classname: String, crossinline transformer:  (ClassNode) -> ByteArray) {
//    addTransformer {
//        val node = ClassNode()
//        ClassReader(it).accept(node, 0)
//
//        if (node.name.replace('/', '.') == classname) transformer(node)
//        else it
//    }
//}
