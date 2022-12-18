package net.yakclient.components.yak.mapping

import net.yakclient.archive.mapper.MappingParser

public interface MappingProvider {
    public val type: String
        get() = parser.name
    public val parser: MappingParser
}