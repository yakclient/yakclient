package dev.extframework.components.extloader.test.extension

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.common.util.resolve
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.components.extloader.test.createAppTarget
import dev.extframework.components.extloader.work
import dev.extframework.components.extloader.workflow.DevWorkflow
import dev.extframework.components.extloader.workflow.DevWorkflowContext
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.test.Test

fun main() {
    TestExtensionComponent().`Load extension`()
}

class TestExtensionComponent {
    private fun readDependenciesList(): Set<String> {
        val ins = this::class.java.getResourceAsStream("/dependencies.txt")!!
        return InputStreamReader(ins).use {
            it.readLines().toSet()
        }
    }

    @Test
    fun `Load extension`() {
        val cache = Path.of(System.getProperty("user.dir")) resolve "src" resolve "test" resolve "resources" resolve "run-cache"

        val archiveGraph = ArchiveGraph.from(cache resolve "archives")
        val dependencyTypes = DependencyTypeContainer(archiveGraph)
        dependencyTypes.register("simple-maven", MavenResolverProvider())

        launch(BootLoggerFactory()) {
            val app = createAppTarget(
                "1.21", cache, archiveGraph, dependencyTypes
            )().merge()

            work(
                cache,
                archiveGraph,
                dependencyTypes,
                DevWorkflowContext(
                    ExtensionDescriptor.parseDescriptor("com.example:test-ext:1.0"),
                    ExtensionRepositorySettings.local(path = this::class.java.getResource("/blackbox-repo")!!.path),
                ),
                DevWorkflow(),
                app
            )().merge()
        }
    }
}