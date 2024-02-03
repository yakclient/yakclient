package net.yakclient.components.extloader.extension.versioning

import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.common.util.readInputStream
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.util.withSlashes
import java.net.URL
import java.nio.ByteBuffer

public class ExtensionSourceProvider(
    private val archive: ExtensionArchiveReference,
) : SourceProvider {
    override val packages: Set<String> = archive.reader.entries()
        .map(ArchiveReference.Entry::name)
        .filter { it.endsWith(".class") }
        .filterNot { it == "module-info.class" }
        .mapTo(HashSet()) { name ->
            val partitionPath = archive.enabledPartitions.find { name.startsWith(it.path) }?.path ?: ""

            val actualName = name.removePrefix(partitionPath)

            actualName // Converting a class path to a package path. Need to remove the class name.
                .split("/")
                .let { it.take(it.size - 1) }
                .joinToString(separator = ".")
        }

    override fun findSource(name: String): ByteBuffer? =
        archive.reader[name.withSlashes() + ".class"]
            ?.resource
            ?.open()
            ?.readInputStream()
            ?.let(ByteBuffer::wrap)

//    override fun getResource(name: String): URL? =
//        archive.reader[name]?.resource?.uri?.toURL()
//
//    override fun getResource(name: String, module: String): URL? = getResource(name)
}