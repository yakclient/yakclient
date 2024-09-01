package dev.extframework.extension.core.minecraft.internal

import com.durganmcbroom.resources.Resource
import dev.extframework.boot.store.DataAccess
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.resolve
import dev.extframework.common.util.toResource
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

internal class MojangMappingAccess(
    private val path: Path
) : DataAccess<String, Resource> {
    override fun read(key: String): Resource? {
        val versionPath = path resolve "client-mappings-$key.json"

        if (!versionPath.exists()) return null

        return versionPath.toUri().toResource()
    }

    override fun write(key: String, value: Resource) {
        val versionPath = path resolve "client-mappings-$key.json"
        versionPath.deleteIfExists()

        runBlocking {
            value.copyTo(versionPath)
        }
    }
}