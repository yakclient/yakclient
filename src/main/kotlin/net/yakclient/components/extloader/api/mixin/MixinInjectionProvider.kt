package net.yakclient.components.extloader.api.mixin

import com.durganmcbroom.jobs.Job
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.components.extloader.api.environment.ApplicationMappingTarget
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.environment.getOrNull
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

public interface MixinInjectionProvider<A : Annotation, T : MixinInjection.InjectionData> {
    public val type: String
    public val annotationType: Class<A>

    public fun parseData(
        context: InjectionContext<A>,
        mappingContext: MappingContext,
        ref: ExtensionArchiveReference
    ): Job<T>

    public fun get(): MixinInjection<T>

    public data class MappingContext(
        val tree: ClassInheritanceTree,
        val mappings: ArchiveMapping,
        val fromNS: String,
        val environment: ExtLoaderEnvironment
    ) {
        val toNS: String = environment[ApplicationMappingTarget].getOrNull()!!.namespace
    }

    public data class InjectionContext<A : Annotation>(
        val element: InjectionElement<A>,
        val classNode: ClassNode,
        val target: ClassNode,
    )

    public data class InjectionElement<A : Annotation>(
        val annotation: A,
        val element: Any,
        val elementType: ElementType
    ) {
        public val methodNode: MethodNode
            get() = if (elementType == ElementType.METHOD) element as MethodNode else unexpectedElement(ElementType.METHOD)
        public val fieldNode: FieldNode
            get() = if (elementType == ElementType.FIELD) element as FieldNode else unexpectedElement(ElementType.FIELD)
        public val classNode: ClassNode
            get() = if (elementType == ElementType.CLASS) element as ClassNode else unexpectedElement(ElementType.CLASS)

        public enum class ElementType {
            CLASS,
            METHOD,
            FIELD;
        }

        private companion object {
            fun InjectionElement<*>.unexpectedElement(expected: ElementType): Nothing {
                throw IllegalArgumentException("Expected element: '$element' to be of type '${expected.name}', however '${element::class.java.name}' was found.")
            }
        }
    }
}