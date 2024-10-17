package dev.extframework.extension.core.minecraft.util

import com.durganmcbroom.resources.openStream
import dev.extframework.archives.ArchiveReference
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.name

internal fun ArchiveReference.write(path: Path) {
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