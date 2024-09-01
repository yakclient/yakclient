package dev.extframework.extension.core.minecraft.internal

import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.archive.mapper.parsers.proguard.ProGuardMappingParser
import dev.extframework.boot.store.CachingDataStore
import dev.extframework.boot.store.DataStore
import dev.extframework.launchermeta.handler.clientMappings
import dev.extframework.launchermeta.handler.loadVersionManifest
import dev.extframework.launchermeta.handler.metadata
import dev.extframework.launchermeta.handler.parseMetadata
import java.nio.file.Path

internal class MojangMappingProvider(
    private val mappingStore: DataStore<String, Resource>
) : MappingsProvider {
    companion object {
        const val REAL_TYPE: String =  "mojang:deobfuscated"
        const val FAKE_TYPE: String =  "mojang:obfuscated"
    }

    constructor(path: Path) : this(CachingDataStore(MojangMappingAccess(path)))

    override val namespaces: Set<String> = setOf(REAL_TYPE, FAKE_TYPE)

    override fun forIdentifier(identifier: String): ArchiveMapping {
        val mappingData = mappingStore[identifier] ?: result {
            val manifest = loadVersionManifest()
            val version = manifest.find(identifier)
                ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
            val m = parseMetadata(version.metadata().merge()).merge().clientMappings().merge()
            mappingStore.put(identifier, m)
            m
        }.getOrThrow()

        return ProGuardMappingParser(FAKE_TYPE, REAL_TYPE).parse(mappingData.openStream())
    }
}