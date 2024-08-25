package dev.extframework.tests.core

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.work
import dev.extframework.components.extloader.workflow.DevWorkflow
import dev.extframework.components.extloader.workflow.DevWorkflowContext
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class BlackboxTest {
    @Test
    fun `Test extension init`() {
        val path = Path.of("tests", "stdlib", "test-extension-init").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            work(
                path,
                graph, types,
                DevWorkflowContext(
                    ExtensionDescriptor.parseDescriptor("dev.extframework.extension:core-blackbox-init-ext:1.0-SNAPSHOT"),
                    ExtensionRepositorySettings.local()
                ),
                DevWorkflow(),
                createEmptyApp()
            )().merge()
        }

        assert(System.getProperty("tests.init") == "true") { "Init not properly called." }
    }

    @Test
    fun `Test extension feature`() {
        val path = Path.of("tests", "stdlib", "test-extension-feature").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            work(
                path,
                graph, types,
                DevWorkflowContext(
                    ExtensionDescriptor.parseDescriptor("dev.extframework.extension:core-blackbox-feature-ext:1.0-SNAPSHOT"),
                    ExtensionRepositorySettings.local()
                ),
                DevWorkflow(),
                createEmptyApp()
            )().merge()
        }

        assert(System.getProperty("tests.feature") == "true")
        assert(System.getProperty("tests.feature.int") == "5")
        assert(System.getProperty("tests.feature.object") == "Strings arent primitives")
    }

    @Test
    fun `Test extension feature delegation`() {
        val path = Path.of("tests", "stdlib", "test-extension-delegation").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            System.setProperty("target.partitions.disabled", "target-test1")

            work(
                path,
                graph, types,
                DevWorkflowContext(
                    ExtensionDescriptor.parseDescriptor("dev.extframework.extension:core-blackbox-feature-delegation-ext:1.0-SNAPSHOT"),
                    ExtensionRepositorySettings.local()
                ),
                DevWorkflow(),
                createEmptyApp()
            )().merge()
        }

        assertTrue(System.getProperty("tests.feature.delegation") == "true")
    }

    @Test
    fun `Test extension app mixin and link`() {
        val path = Path.of("tests", "stdlib", "test-extension-link").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            work(
                path,
                graph, types,
                DevWorkflowContext(
                    ExtensionDescriptor.parseDescriptor("dev.extframework.extension:core-blackbox-link-ext:1.0-SNAPSHOT"),
                    ExtensionRepositorySettings.local()
                ),
                DevWorkflow(),
                createBlackboxApp(Path.of("").toAbsolutePath().parent.resolve("core/blackbox-app/build/libs/core-blackbox-app-1.0-SNAPSHOT.jar"))
            )().merge()
        }

        assertTrue(System.getProperty("tests.app") == "true", "App never was called")
        assertTrue(System.getProperty("tests.app.mixin") == "true", "Mixins didnt apply")
    }
}