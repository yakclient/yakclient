package dev.extframework.extension.core.mixin

import dev.extframework.archives.mixin.MixinInjection

public interface MixinTransaction {
    public data class Metadata<T : MixinInjection.InjectionData>(
        val destination: String, // Dot format, ie org.example.ClassA
        val data: T,
        val injection: MixinInjection<T>
    )
}