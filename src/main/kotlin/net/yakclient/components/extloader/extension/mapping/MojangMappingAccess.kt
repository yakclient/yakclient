package net.yakclient.components.extloader.extension.mapping

import com.durganmcbroom.resources.Resource
import kotlinx.coroutines.runBlocking
import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.resolve
import net.yakclient.common.util.toResource
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

public class MojangMappingAccess(
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