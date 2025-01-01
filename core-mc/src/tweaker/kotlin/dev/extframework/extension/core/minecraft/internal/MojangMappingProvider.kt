package dev.extframework.extension.core.minecraft.internal

import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.toByteArray
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.archive.mapper.parsers.proguard.ProGuardMappingParser
import dev.extframework.boot.store.CachingDataStore
import dev.extframework.boot.store.DataStore
import dev.extframework.extension.core.minecraft.environment.MappingNamespace
import dev.extframework.launchermeta.handler.clientMappings
import dev.extframework.launchermeta.handler.loadVersionManifest
import dev.extframework.launchermeta.handler.metadata
import dev.extframework.launchermeta.handler.parseMetadata
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.nio.file.Path

internal class MojangMappingProvider(
    private val mappingStore: DataStore<String, Resource>
) : MappingsProvider {
    companion object {
        val DEOBF_TYPE = MappingNamespace("mojang", "deobfuscated")
        val OBF_TYPE = MappingNamespace("mojang","obfuscated")
    }

    constructor(path: Path) : this(CachingDataStore(MojangMappingAccess(path)))

    override val namespaces: Set<String> = setOf(DEOBF_TYPE.identifier, OBF_TYPE.identifier)

    override fun forIdentifier(identifier: String): ArchiveMapping = runBlocking {
        val mappingData = mappingStore[identifier] ?: run {
            val manifest = loadVersionManifest()
            val version = manifest.find(identifier)
                ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
            val m = parseMetadata(version.metadata().getOrThrow()).getOrThrow().clientMappings().getOrThrow()
            mappingStore.put(identifier, m)
            m
        }

        ProGuardMappingParser(OBF_TYPE.identifier, DEOBF_TYPE.identifier).parse(ByteArrayInputStream(mappingData.open().toByteArray()))
    }
}