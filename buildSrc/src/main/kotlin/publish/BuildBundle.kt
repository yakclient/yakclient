package publish

import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
import dev.extframework.archives.ArchiveReference
import dev.extframework.common.util.readInputStream
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.name

abstract class BuildBundle : DefaultTask() {
    @get:OutputFile
    val bundlePath: File =
        (project.layout.buildDirectory.asFile.get().toPath().resolve("libs").resolve("extension.bundle")).toFile()

    @get:InputFiles
    abstract val metadata: ConfigurableFileCollection

    @get:InputFiles
    abstract val erm: ConfigurableFileCollection

    @get:Internal
    abstract val partitions: MapProperty<String, FileCollection>

    interface PartitionConfiguration {
        fun jar(task: Provider<out Task>)
        fun prm(task: Provider<out Task>)
    }

    fun partition(name: String, configure: Action<PartitionConfiguration>) {

        val configValues = object : PartitionConfiguration {
            var jar: FileCollection? = null
            var prm: FileCollection? = null

            override fun jar(task: Provider<out Task>) {
                jar = task.get().outputs.files
            }

            override fun prm(task: Provider<out Task>) {
                prm = task.get().outputs.files
            }
        }
        configure.execute(configValues)

        val prm = checkNotNull(configValues.prm) { "You must specific a PRM!" }
        val jar = checkNotNull(configValues.jar) { "You must specific a jar!" }

        partitions.put(name, prm + jar)
    }

    @TaskAction
    fun generateErm() {
        val archive = emptyArchiveReference()

        archive.writer.put(
            ArchiveReference.Entry(
                "erm.json",
                Resource("<heap>") {
                    FileInputStream(erm.files.first())
                },
                false,
                archive
            )
        )
        archive.writer.put(
            ArchiveReference.Entry(
                "metadata.json",
                Resource("<heap>") {
                    FileInputStream(metadata.files.first())
                },
                false,
                archive
            )
        )

        partitions.get().forEach { (name, files) ->
            files.forEach { file ->
                archive.writer.put(
                    ArchiveReference.Entry(
                        "${name}.${file.extension}",
                        Resource("<heap>") {
                            FileInputStream(file)
                        },
                        false,
                        archive
                    )
                )
            }
        }

        val entries = archive.reader.entries().toMutableList()
        listOf("md5", "sha1", "sha256", "sha512").forEach {
            computeHashes(entries, it)
        }

        archive.write(bundlePath.toPath())
    }

    private fun computeHashes(
        entries: List<ArchiveReference.Entry>,
        _hashType: String
    ) {
        val hashType = _hashType.lowercase()

        val engine = MessageDigest.getInstance(hashType)

        entries
            .filterNot { it.isDirectory }
            .forEach { entry ->
                val digest = engine.digest(
                    entry.resource.openStream().readInputStream(),
                )

                entry.handle.writer.put(
                    ArchiveReference.Entry(
                        entry.name + "." + hashType,
                        Resource("<heap>") {
                            HexFormat.of().formatHex(digest).byteInputStream()
                        },
                        false,
                        entry.handle
                    )
                )

                engine.reset()
            }
    }

    private fun emptyArchiveReference(
        location: URI = URI("archive:empty")
    ): ArchiveReference = object : ArchiveReference {
        private val entries: MutableMap<String, () -> ArchiveReference.Entry> = HashMap()
        override val isClosed: Boolean = false
        override val location: URI = location
        override val modified: Boolean = entries.isNotEmpty()
        override val name: String? = null
        override val reader: ArchiveReference.Reader = object : ArchiveReference.Reader {
            override fun entries(): Sequence<ArchiveReference.Entry> {
                return entries.values.asSequence().map { it() }
            }

            override fun of(name: String): ArchiveReference.Entry? {
                return entries[name]?.invoke()
            }
        }
        override val writer = object : ArchiveReference.Writer {
            override fun put(entry: ArchiveReference.Entry) {
                entries[entry.name] = { entry }
            }

            override fun remove(name: String) {
                entries.remove(name)
            }

        }

        override fun close() {}
    }

    private fun ArchiveReference.write(path: Path) {
        val temp = Files.createTempFile(path.name, "jar")

        JarOutputStream(FileOutputStream(temp.toFile())).use { target ->
            reader.entries().forEach { e ->
                val entry = JarEntry(e.name)

                target.putNextEntry(entry)

                val eIn = e.resource.openStream()

                //Stolen from https://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
                val buffer = ByteArray(1024)

                while (true) {
                    val count: Int = eIn.read(buffer)
                    if (count == -1) break

                    target.write(buffer, 0, count)
                }

                target.closeEntry()
            }
        }

        Files.copy(temp, path, StandardCopyOption.REPLACE_EXISTING)
    }
}