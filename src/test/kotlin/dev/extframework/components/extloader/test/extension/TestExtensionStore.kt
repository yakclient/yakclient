package dev.extframework.components.extloader.test.extension
//
//import dev.extframework.boot.store.CachingDataStore
//import dev.extframework.components.yak.extension.ErmRepository
//import dev.extframework.components.yak.extension.ExtensionDataAccess
//import dev.extframework.components.yak.extension.ExtensionRuntimeModel
//import dev.extframework.components.yak.extension.artifact.ExtensionDescriptor
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
//        "dev.extframework.example",
//        "example",
//        "1.0-SNAPSHOT",
//        null
//    )
//
//    fun `Generate test erm stub`(): ExtensionRuntimeModel = ExtensionRuntimeModel(
//        "dev.extframework.example",
//        "example",
//        "1.0-SNAPSHOT",
//        "none",
//        "dev.extframework.example.ExamplePlugin",
//        listOf(
//            ErmRepository(
//                "simple-maven",
//                mapOf(
//                    "url" to "https://maven.extframework.dev/snapshots"
//                )
//            )
//        ),
//        listOf(
//            mapOf(
//                "descriptor" to "dev.extframework:archives-mixin:1.0-SNAPSHOT"
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