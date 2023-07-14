package net.yakclient.components.extloader.test.extension
//
//import net.yakclient.boot.store.CachingDataStore
//import net.yakclient.components.yak.extension.ErmRepository
//import net.yakclient.components.yak.extension.ExtensionDataAccess
//import net.yakclient.components.yak.extension.ExtensionRuntimeModel
//import net.yakclient.components.yak.extension.artifact.ExtensionDescriptor
//import org.junit.jupiter.api.Assertions
//import java.nio.file.Path
//import kotlin.test.Test
//
//class TestExtensionStore {
//    val store = CachingDataStore(
//        ExtensionDataAccess(
//        Path.of(System.getProperty("java.io.tmpdir"))
//    )
//    )
//
//    fun `Generate test erm descriptor`() = ExtensionDescriptor(
//        "net.yakclient.example",
//        "example",
//        "1.0-SNAPSHOT",
//        null
//    )
//
//    fun `Generate test erm stub`(): ExtensionRuntimeModel = ExtensionRuntimeModel(
//        "net.yakclient.example",
//        "example",
//        "1.0-SNAPSHOT",
//        "none",
//        "net.yakclient.example.ExamplePlugin",
//        listOf(
//            ErmRepository(
//                "simple-maven",
//                mapOf(
//                    "url" to "http://maven.yakclient.net/snapshots"
//                )
//            )
//        ),
//        listOf(
//            mapOf(
//                "descriptor" to "net.yakclient:archives-mixin:1.0-SNAPSHOT"
//            )
//        ),
//        listOf(),
//        listOf()
//
//    )
//
//    fun `Test ext store write`() {
//        store.put(
//            `Generate test erm descriptor`(),
//            `Generate test erm stub`()
//        )
//    }
//
//    fun `Test ext store read`() {
//        val datum = store[`Generate test erm descriptor`()]
//
//        println(datum)
//
//        Assertions.assertEquals(datum, `Generate test erm stub`())
//    }
//
//    @Test
//    fun `Run full ext store test`() {
//        `Test ext store write`()
//        `Test ext store read`()
//    }
//}