package dev.extframework.extension.core.minecraft.remap

import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.transform.ClassInheritanceTree
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey

public interface ExtensionRemapper : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionRemapper

    public companion object : EnvironmentAttributeKey<ExtensionRemapper>

    public val priority: Int
        get() = 0

    public fun remap(
        mappings: ArchiveMapping,
        inheritanceTree: ClassInheritanceTree,
        source: String,
        target: String,
    ): TransformerConfig
}