package net.yakclient.components.extloader.extension.mapping

import kotlinx.coroutines.runBlocking
import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.common.util.toResource
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

public class MojangMappingAccess(
        private val path: Path
) : DataAccess<String, SafeResource> {
    override fun read(key: String): SafeResource? {
        val versionPath = path resolve "client-mappings-$key.json"

        if (!versionPath.exists()) return null

        return versionPath.toUri().toResource()
    }

    override fun write(key: String, value: SafeResource) {
        val versionPath = path resolve "client-mappings-$key.json"
        versionPath.deleteIfExists()

        runBlocking {
            value.copyTo(versionPath)
        }
    }
}