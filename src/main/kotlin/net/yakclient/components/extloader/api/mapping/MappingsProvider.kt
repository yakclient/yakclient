package net.yakclient.components.extloader.api.mapping

import net.yakclient.archive.mapper.ArchiveMapping

public interface MappingsProvider {
    public val namespaces: Set<String>

    public fun forIdentifier(identifier: String): ArchiveMapping
}