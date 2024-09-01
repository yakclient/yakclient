//package dev.extframework.extloader.extension.mapping
//
//import com.durganmcbroom.resources.Resource
//import com.durganmcbroom.resources.toResource
//import kotlinx.coroutines.runBlocking
//import dev.extframework.boot.store.DataAccess
//import dev.extframework.common.util.copyTo
//import dev.extframework.common.util.resolve
//import java.nio.file.Path
//import kotlin.io.path.deleteIfExists
//import kotlin.io.path.exists
//
//public class MojangMappingAccess(
//        private val path: Path
//) : DataAccess<String, Resource> {
//    override fun read(key: String): Resource? {
//        val versionPath = path resolve "client-mappings-$key.json"
//
//        if (!versionPath.exists()) return null
//
//        return versionPath.toResource()
//    }
//
//    override fun write(key: String, value: Resource) {
//        val versionPath = path resolve "client-mappings-$key.json"
//        versionPath.deleteIfExists()
//
//        runBlocking {
//            value.copyTo(versionPath)
//        }
//    }
//}