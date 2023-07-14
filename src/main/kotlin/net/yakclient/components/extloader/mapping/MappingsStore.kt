package net.yakclient.components.extloader.mapping

import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.readInputStream
import net.yakclient.common.util.resolve
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.writeBytes

public class MappingsStore(
    private val path: Path
) : DataAccess<String, InputStream> {
    override fun read(key: String): InputStream? {
        return (path resolve "$key.txt")
            .toFile()
            .takeIf(File::exists)
            ?.inputStream()
    }

    override fun write(key: String, value: InputStream) {
        (path resolve "$key.txt").writeBytes(value.readInputStream())
    }
}