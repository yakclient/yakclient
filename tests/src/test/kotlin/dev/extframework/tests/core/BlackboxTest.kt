package dev.extframework.tests.core

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.common.util.resolve
import dev.extframework.extloader.InternalExtensionEnvironment
import dev.extframework.extloader.initExtensions
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.ValueAttribute
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class BlackboxTest {
    @Test
    fun `Test extension init`() {
        val path = Path.of("tests", "core", "test-extension-init").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            initExtensions(
                mapOf(
                    ExtensionDescriptor.parseDescriptor(
                        "dev.extframework.extension:core-blackbox-init-ext:1.0-SNAPSHOT"
                    ) to ExtensionRepositorySettings.local()
                ),
                InternalExtensionEnvironment(
                    path, graph, types, createEmptyApp()
                )
            )().merge()
        }

        assert(System.getProperty("tests.init") == "true") { "Init not properly called." }
    }

    @Test
    fun `Test extension feature`() {
        val path = Path.of("tests", "core", "test-extension-feature").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            initExtensions(
                mapOf(
                    ExtensionDescriptor.parseDescriptor(
                        "dev.extframework.extension:core-blackbox-feature-ext:1.0-SNAPSHOT"
                    ) to ExtensionRepositorySettings.local()
                ),
                InternalExtensionEnvironment(
                    path, graph, types, createEmptyApp()
                )
            )().merge()
        }

        assert(System.getProperty("tests.feature") == "true")
        assert(System.getProperty("tests.feature.int") == "5")
        assert(System.getProperty("tests.feature.object") == "Strings arent primitives")
    }

    @Test
    fun `Test extension feature delegation`() {
        val path = Path.of("tests", "core", "test-extension-delegation").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            System.setProperty("target.partitions.disabled", "target-test1")

            initExtensions(
                mapOf(
                    ExtensionDescriptor.parseDescriptor(
                        "dev.extframework.extension:core-blackbox-feature-delegation-ext:1.0-SNAPSHOT"
                    ) to ExtensionRepositorySettings.local()
                ),
                InternalExtensionEnvironment(
                    path, graph, types, createEmptyApp()
                )
            )().merge()
        }

        assertTrue(System.getProperty("tests.feature.delegation") == "true")
    }

    @Test
    fun `Test extension app mixin and link`() {
        val path = Path.of("tests", "core", "test-extension-link").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            initExtensions(
                mapOf(
                    ExtensionDescriptor.parseDescriptor(
                        "dev.extframework.extension:core-blackbox-link-ext:1.0-SNAPSHOT"
                    ) to ExtensionRepositorySettings.local()
                ),
                InternalExtensionEnvironment(
                    path, graph, types,
                    createBlackboxApp(
                        Path.of("")
                            .toAbsolutePath()
                            .parent
                            .resolve("core/blackbox-app/build/libs/core-blackbox-app-1.0-SNAPSHOT.jar")

                    )
                )
            )().merge()
        }

        assertTrue(System.getProperty("tests.app") == "true", "App never was called")
        assertTrue(System.getProperty("tests.app.mixin") == "true", "Mixins didnt apply")
    }

    @Test
    fun `Test minecraft core`() {
        val path = Path.of("tests", "core-mc", "test-minecraft-core").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            val environment = InternalExtensionEnvironment(
                path, graph, types,
                createMinecraftApp(
                    path resolve "minecraft",
                    "1.21",
                    graph, types,
                )().merge()
            )
            System.setProperty("mapping.target", "mojang:obfuscated")
            initExtensions(
                mapOf(
                    ExtensionDescriptor.parseDescriptor(
                        "org.example:portfolio-extension:1.0.1-BETA"
                    ) to ExtensionRepositorySettings.local()
                ),
                environment
            )().merge()

            val app = environment[ApplicationTarget].extract().node.handle!!.classloader

            val mainClass = app.loadClass(
                "net.minecraft.client.main.Main"
            )

            mainClass.getMethod("main", Array<String>::class.java).invoke(
                null, arrayOf<String>(
                    "--version", "1.21", "--accessToken", ""
                )
            )
        }
    }
}
