package dev.extframework.extension.core.minecraft.internal

import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.transform.ClassInheritanceTree
import dev.extframework.archive.mapper.transform.mappingTransformConfigFor
import dev.extframework.archives.transform.TransformerConfig
import dev.extframework.extension.core.minecraft.remap.ExtensionRemapper

public class RootRemapper : ExtensionRemapper {
    override fun remap(
        mappings: ArchiveMapping,
        inheritanceTree: ClassInheritanceTree,
        source: String,
        target: String
    ): TransformerConfig = mappingTransformConfigFor(
        mappings,
        source, target,
        inheritanceTree,
    )
}