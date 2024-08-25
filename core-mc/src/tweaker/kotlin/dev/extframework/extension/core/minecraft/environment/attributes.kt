package dev.extframework.extension.core.minecraft.environment

import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.extension.core.minecraft.remap.ExtensionRemapper
import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey
import dev.extframework.internal.api.environment.MutableObjectSetAttribute

public val mappingProvidersAttrKey: MutableObjectSetAttribute.Key<MappingsProvider> =
    MutableObjectSetAttribute.Key("mapping-providers")
public val remappersAttrKey: MutableObjectSetAttribute.Key<ExtensionRemapper> =
    MutableObjectSetAttribute.Key("remappers")

public data class ApplicationMappingTarget(
    public val namespace: String
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ApplicationMappingTarget

    public companion object : EnvironmentAttributeKey<ApplicationMappingTarget>
}