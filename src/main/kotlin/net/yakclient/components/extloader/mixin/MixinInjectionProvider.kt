package net.yakclient.components.extloader.mixin

import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.components.extloader.extension.archive.ExtensionArchiveReference

public interface MixinInjectionProvider<T: MixinInjection.InjectionData> {
    public val type: String

    public fun parseData(options: Map<String, String>, ref: ExtensionArchiveReference) : T

    public fun get() : MixinInjection<T>
}

//public fun <T: MixinInjection.InjectionData>  MixinInjectionProvider<T>.apply(data: Map<String, String>, ref: ArchiveReference) : TransformerConfig.Mutable = get().apply(parseData(data, ref))