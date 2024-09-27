package dev.extframework.gradle.publish

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.extframework.common.util.readInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

abstract class ExtensionPublishTask : DefaultTask() {
    private val mapper = jacksonObjectMapper()

    @get:Input
    abstract val bundle: Property<File>

    @TaskAction
    fun publish() {
        val ext = project.extensions.getByType(PublishingExtension::class.java)

        val repositories = ext.repositories.filterIsInstance<DefaultMavenArtifactRepository>()

        val publications = ext.publications.filterIsInstance<ExtensionPublication>()

        repositories.forEach { repository ->
            publications.forEach { publication ->
                val url = URL("${repository.url}/registry")

                val conn = url.openConnection() as HttpURLConnection
                conn.doOutput = true

                conn.requestMethod = "PUT"
                conn.setRequestProperty("Authorization", "Bearer ${repository.credentials.password}")

                FileInputStream(bundle.get()).transferTo(conn.outputStream)

                conn.connect()

                if (conn.responseCode != 200) {
                    System.out.println(String(conn.errorStream.readInputStream()))
                    throw Exception("Failed to publish. Error code: '${conn.responseCode}': '${conn.responseMessage}'")
                }

            }
        }
    }

//    fun buildBundle(
//        metadata: ExtensionMetadata,
//        erm: ExtensionRuntimeModel
//        extFrameworkExtension: ExtFrameworkExtension
//    ) {
//        val archive = emptyArchiveReference()
//        archive.writer.put(ArchiveReference.Entry(
//                "erm.json",
//                Resource("<heap>") {
//                    ByteArrayInputStream(mapper.writeValueAsBytes(metadata))
//                },
//                false,
//                archive
//        ))
//
//        archive.writer.put(ArchiveReference.Entry(
//            "erm.json",
//            Resource("<heap>") {
//                ByteArrayInputStream(mapper.writeValueAsBytes(metadata))
//            },
//            false,
//            archive
//        ))
//    }
}