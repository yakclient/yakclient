package dev.extframework.extension.core.minecraft.environment

import java.nio.file.Path

public data class MappingNamespace(
    val organization: String,
    val name: String
) {
    val path: Path = Path.of(organization, name)
    val identifier: String = "$organization:$name"

    public companion object {
        public fun parse(identifier: String): MappingNamespace = identifier.split(":").let { (org, name) ->
            MappingNamespace(org, name)
        }
    }
}