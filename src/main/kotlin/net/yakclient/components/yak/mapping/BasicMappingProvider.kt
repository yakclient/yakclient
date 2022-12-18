package net.yakclient.components.yak.mapping

import net.yakclient.archive.mapper.MappingParser
import net.yakclient.archive.mapper.Parsers

public open class BasicMappingProvider(override val parser: MappingParser) : MappingProvider

public object ProGuardMappingParser : BasicMappingProvider(Parsers[Parsers.PRO_GUARD]!!)