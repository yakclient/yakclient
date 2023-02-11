package net.yakclient.components.yak.mixin

import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.archives.transform.TransformerConfig

public interface MixinInjectionProvider<T: MixinInjection.InjectionData> {
    public val type: String

    public fun parseData(options: Map<String, String>, ref: ArchiveReference) : T

    public fun get() : MixinInjection<T>
}

public fun <T: MixinInjection.InjectionData>  MixinInjectionProvider<T>.apply(data: Map<String, String>, ref: ArchiveReference) : TransformerConfig.MutableTransformerConfiguration = get().apply(parseData(data, ref))