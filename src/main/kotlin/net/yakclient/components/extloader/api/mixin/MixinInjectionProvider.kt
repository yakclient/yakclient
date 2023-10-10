package net.yakclient.components.extloader.api.mixin

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.mapping.MappingsProvider

public interface MixinInjectionProvider<T: MixinInjection.InjectionData> {
    public val type: String

    public fun parseData(
        options: Map<String, String>,
        mappingContext: MappingContext,
        ref: ExtensionArchiveReference
    ) : T

    public fun get() : MixinInjection<T>

    public data class MappingContext(
            val tree: ClassInheritanceTree,
            val mappings: ArchiveMapping,
            val environment: ExtLoaderEnvironment
    )
}