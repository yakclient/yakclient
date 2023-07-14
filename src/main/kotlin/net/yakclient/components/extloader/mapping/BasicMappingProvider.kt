package net.yakclient.components.extloader.mapping

import net.yakclient.archive.mapper.MappingParser

public open class BasicMappingProvider(override val parser: MappingParser) : MappingProvider

public object ProGuardMappingParser : BasicMappingProvider(net.yakclient.archive.mapper.parsers.ProGuardMappingParser)