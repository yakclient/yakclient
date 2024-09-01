//package dev.extframework.extloader.extension.mapping
//
//import com.durganmcbroom.jobs.mapException
//import com.durganmcbroom.jobs.result
//import com.durganmcbroom.resources.Resource
//import com.durganmcbroom.resources.openStream
//import dev.extframework.archive.mapper.ArchiveMapping
//import dev.extframework.archive.mapper.MappingsProvider
//import dev.extframework.archive.mapper.parsers.proguard.ProGuardMappingParser
//import dev.extframework.boot.store.CachingDataStore
//import dev.extframework.boot.store.DataStore
//import dev.extframework.extloader.exception.ExtLoaderExceptions
//import dev.extframework.internal.api.exception.StructuredException
//import dev.extframework.launchermeta.handler.clientMappings
//import dev.extframework.launchermeta.handler.loadVersionManifest
//import dev.extframework.launchermeta.handler.metadata
//import dev.extframework.launchermeta.handler.parseMetadata
//import java.nio.file.Path
//
//public class MojangExtensionMappingProvider (
//    private val path: Path,
//) : MappingsProvider {
//    private val mappingStore: DataStore<String, Resource> = CachingDataStore(MojangMappingAccess(path))
//
//    public companion object {
//        public const val DEOBFUSCATED: String = "mojang:deobfuscated"
//        public const val OBFUSCATED: String = "mojang:obfuscated"
//    }
//
//    override val namespaces: Set<String> = setOf(DEOBFUSCATED, OBFUSCATED)
//
//    override fun forIdentifier(identifier: String): ArchiveMapping {
//        val mappingData = mappingStore[identifier] ?: result {
//            val manifest = loadVersionManifest()
//            val version = manifest.find(identifier)
//                ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
//            val m = parseMetadata(version.metadata().merge()).merge().clientMappings().merge()
//            mappingStore.put(identifier, m)
//            m
//        }.mapException {
//            StructuredException(
//                ExtLoaderExceptions.MinecraftResourceException,
//                it,
//                "There has been an error loading 'Mojang' (official) mappings for version: '$identifier'"
//            ) {
//                // Could be other things as well so this is a little risky
//                solution("Check your network connection and try again.")
//
//                path asContext "Mapping cache location"
//            }
//        }.getOrThrow()
//
//        return ProGuardMappingParser(OBFUSCATED, DEOBFUSCATED).parse(mappingData.openStream())
//    }
//}