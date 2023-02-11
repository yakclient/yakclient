package net.yakclient.components.yak.extension.versioning

import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.common.util.readInputStream
import net.yakclient.components.yak.mapping.withDots
import net.yakclient.components.yak.mapping.withSlashes
import java.net.URL
import java.nio.ByteBuffer

private const val VERSIONING_PATH = "META-INF/versioning/partitions/"

public class PartitionedVersioningSourceProvider(
    private val enabledPartitions: Set<String>,
    private val archive: ArchiveReference
) : SourceProvider {
    override val packages: Set<String> = archive.reader.entries()
        .map(ArchiveReference.Entry::name)
        .filter { it.endsWith(".class") }
        .filterNot { it == "module-info.class" }
        .filter {
            val startsWith = it.startsWith(VERSIONING_PATH)

            (startsWith && enabledPartitions.contains(
                it.removeSuffix(VERSIONING_PATH).split("/").first()
            )) || !startsWith
        }
        .map { it.removeSuffix(VERSIONING_PATH) }
        .mapTo(HashSet()) { it.removeSuffix(".class").withDots() }

    override fun getSource(name: String): ByteBuffer? =
         (archive.reader[VERSIONING_PATH + name.withSlashes() + ".class"] ?: archive.reader[name.withSlashes() + ".class"])?.resource?.open()?.readInputStream()?.let(ByteBuffer::wrap)

    override fun getResource(name: String): URL? = (archive.reader[VERSIONING_PATH + name] ?: archive.reader[name])?.resource?.uri?.toURL()

    override fun getResource(name: String, module: String): URL? = getResource(name)
}