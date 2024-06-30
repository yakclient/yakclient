package dev.extframework.components.extloader.extension

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.store.DataAccess
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.extension.artifact.ExtensionDescriptor
import dev.extframework.components.extloader.api.extension.ExtensionRuntimeModel
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

public class ExtensionDataAccess(
    private val path: Path,
) : DataAccess<ExtensionDescriptor, ExtensionRuntimeModel> {
    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private fun createPath(descriptor: ExtensionDescriptor) : Path =  path resolve descriptor.group.replace(
        '.',
        File.separatorChar
    ) resolve descriptor.artifact resolve descriptor.version resolve "${descriptor.artifact}-${descriptor.version}-erm.json"

    override fun read(key: ExtensionDescriptor): ExtensionRuntimeModel? {
        val path = createPath(key)
        if (!Files.exists(path)) return null

        return mapper.readValue<ExtensionRuntimeModel>(path.toFile())
    }

    override fun write(key: ExtensionDescriptor, value: ExtensionRuntimeModel) {
        val path = createPath(key)
        if (!Files.exists(path)) path.make()

        path.writeBytes(mapper.writeValueAsBytes(value))
    }
}