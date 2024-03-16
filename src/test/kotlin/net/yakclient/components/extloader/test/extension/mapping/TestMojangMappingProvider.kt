package net.yakclient.components.extloader.test.extension.mapping

import com.durganmcbroom.resources.Resource
import net.yakclient.boot.store.DataStore
import net.yakclient.boot.store.DelegatingDataStore
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import net.yakclient.components.extloader.extension.mapping.MojangMappingAccess
import java.lang.IllegalStateException
import java.nio.file.Files
import kotlin.test.Test

class TestMojangMappingProvider {
    @Test
    fun `Test create mapper throws no errors`() {
        val path = Files.createTempDirectory("")
        val provider = MojangExtensionMappingProvider(path)
        val one = provider.forIdentifier("1.20.1")
        val two = provider.forIdentifier("1.16.4")

        assert(one.classes.values.size >= 500)
        assert(two.classes.values.size >= 500)
    }

    @Test
    fun `Test caching works correctly`() {
        val path = Files.createTempDirectory("")
        println(path)
        var shouldPutBeCalled = true
        val provider = MojangExtensionMappingProvider(object : DelegatingDataStore<String, Resource>(MojangMappingAccess(path)) {
            override fun put(key: String, value: Resource) {
                if (!shouldPutBeCalled) throw IllegalStateException("Caching not working")
                super.put(key, value)
            }
        })

        provider.forIdentifier("1.20.1")
        provider.forIdentifier("1.19.1")
        shouldPutBeCalled = false
        provider.forIdentifier("1.19.1")
        provider.forIdentifier("1.20.1")
    }
}