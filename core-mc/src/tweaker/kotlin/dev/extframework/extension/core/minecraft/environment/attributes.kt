package dev.extframework.extension.core.minecraft.environment

import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.extension.core.minecraft.remap.ExtensionRemapper
import dev.extframework.tooling.api.environment.MutableObjectSetAttribute
import dev.extframework.tooling.api.environment.ValueAttribute

public val mappingProvidersAttrKey: MutableObjectSetAttribute.Key<MappingsProvider> =
    MutableObjectSetAttribute.Key("mapping-providers")
public val remappersAttrKey: MutableObjectSetAttribute.Key<ExtensionRemapper> =
    MutableObjectSetAttribute.Key("remappers")
public val mappingTargetAttrKey : ValueAttribute.Key<MappingNamespace> = ValueAttribute.Key("mapping-target")