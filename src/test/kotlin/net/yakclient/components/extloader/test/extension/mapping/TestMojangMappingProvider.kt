package net.yakclient.components.extloader.test.extension.mapping

import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
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

}