package net.yakclient.components.extloader.extension.mapping

import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.MappingsProvider
import net.yakclient.archive.mapper.parsers.proguard.ProGuardMappingParser
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.store.DataStore
import net.yakclient.components.extloader.api.exception.StructuredException
import net.yakclient.components.extloader.exception.ExtLoaderExceptions
import net.yakclient.launchermeta.handler.clientMappings
import net.yakclient.launchermeta.handler.loadVersionManifest
import net.yakclient.launchermeta.handler.metadata
import net.yakclient.launchermeta.handler.parseMetadata
import java.nio.file.Path

public class MojangExtensionMappingProvider (
    private val path: Path,
) : MappingsProvider {
    private val mappingStore: DataStore<String, Resource> = CachingDataStore(MojangMappingAccess(path))

    public companion object {
        public const val REAL_TYPE: String = "mojang:deobfuscated"
        public const val FAKE_TYPE: String = "mojang:obfuscated"
    }

    override val namespaces: Set<String> = setOf(REAL_TYPE, FAKE_TYPE)

    override fun forIdentifier(identifier: String): ArchiveMapping {
        val mappingData = mappingStore[identifier] ?: result {
            val manifest = loadVersionManifest()
            val version = manifest.find(identifier)
                ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
            val m = parseMetadata(version.metadata().merge()).merge().clientMappings().merge()
            mappingStore.put(identifier, m)
            m
        }.mapException {
            StructuredException(
                ExtLoaderExceptions.MinecraftResourceException,
                it,
                "There has been an error loading 'Mojang' (official) mappings for version: '$identifier'"
            ) {
                // Could be other things as well so this is a little risky
                solution("Check your network connection and try again.")

                path asContext "Mapping cache location"
            }
        }.getOrThrow()

        return ProGuardMappingParser(FAKE_TYPE, REAL_TYPE).parse(mappingData.openStream())
    }
}