package dev.extframework.tests.core

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.extloader.InternalExtensionEnvironment
import dev.extframework.extloader.environment.registerLoaders
import dev.extframework.tooling.api.environment.MutableObjectContainerAttribute
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.environment.partitionLoadersAttrKey
import dev.extframework.tooling.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class ExtLoaderTests {
    @Test
    fun `Test two similar extensions cant load twice`() {
        val path = Path("tests", "ext-loader", "test-extensions-twice").toAbsolutePath()

        launch(BootLoggerFactory()) {
            val (graph, types) = setupBoot(path)

            val environment = InternalExtensionEnvironment(
                path, graph, types, createEmptyApp()
            )

            environment.setUnless(MutableObjectContainerAttribute(partitionLoadersAttrKey))
            environment[partitionLoadersAttrKey].extract().registerLoaders()
            environment.archiveGraph.registerResolver(environment.extensionResolver.partitionResolver)

            var ext1 = ExtensionDescriptor.parseDescriptor(
                "dev.extframework.extension:double-load:1.0"
            )
            var ext11 = ExtensionDescriptor.parseDescriptor(
                "dev.extframework.extension:double-load:1.1"
            )
            environment.archiveGraph.cache(
                ExtensionArtifactRequest(
                    ext1,
                ),
                ExtensionRepositorySettings.local(),
                environment.extensionResolver
            )().merge()
            environment.archiveGraph.cache(
                ExtensionArtifactRequest(
                    ext11,
                ),
                ExtensionRepositorySettings.local(),
                environment.extensionResolver
            )().merge()

            var ext1Node = environment.archiveGraph.get(
                ext1,
                environment.extensionResolver
            )().merge()

            var ext11Node = environment.archiveGraph.get(
                ext11,
                environment.extensionResolver
            )().merge()

            check(ext1Node == ext11Node)
        }
    }
}