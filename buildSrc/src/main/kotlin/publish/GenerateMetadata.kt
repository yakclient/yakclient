package publish

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.provider.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import dev.extframework.common.util.make
import org.gradle.api.Action
import org.gradle.api.tasks.Internal

abstract class GenerateMetadata : DefaultTask() {
    @get:Internal
    val metadata: Property<MutableExtensionMetadata> = project.objects.property(MutableExtensionMetadata::class.java).convention(
        MutableExtensionMetadata(
            project.objects.property(String::class.java),
            project.objects.listProperty(String::class.java),
            project.objects.property(String::class.java),
            project.objects.property(String::class.java),
            project.objects.listProperty(String::class.java),
            project.objects.property(String::class.java),
        )
    )

    @get:OutputFile
    val outputFile: File =
        (project.layout.buildDirectory.asFile.get().toPath().resolve("libs").resolve("metadata.json")).toFile()

    fun metadata(action: Action<MutableExtensionMetadata>) {
        action.execute(metadata.get())
    }

    @TaskAction
    fun action() {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(
                SimpleModule()
                    .addSerializer(Property::class.java, ProviderSerializer())
                    .addSerializer(SetProperty::class.java, SetPropertySerializer())
                    .addSerializer(MapProperty::class.java, MapPropertySerializer())
                    .addSerializer(ListProperty::class.java, ListPropertySerializer())
            )

        val bytes = mapper.writeValueAsBytes(metadata.get())

        outputFile.toPath().make()
        outputFile.writeBytes(bytes)
    }
}

data class MutableExtensionMetadata(
    val name: Property<String>,
    val developers: ListProperty<String>,
    val icon: Property<String?>,
    val description: Property<String>,
    val tags: ListProperty<String>,
    val app: Property<String>
)


class SetPropertySerializer : JsonSerializer<SetProperty<*>>() {
    override fun serialize(value: SetProperty<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeStartArray()
            gen.writeEndArray()
        }
    }
}

class ListPropertySerializer : JsonSerializer<ListProperty<*>>() {
    override fun serialize(value: ListProperty<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeStartArray()
            gen.writeEndArray()
        }
    }
}

class MapPropertySerializer : JsonSerializer<MapProperty<*, *>>() {
    override fun serialize(value: MapProperty<*, *>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        val map = value.get()

        map.forEach {
            gen.writeObjectField(it.key.toString(), it.value)
        }
        gen.writeEndObject()
    }
}

class ProviderSerializer : JsonSerializer<Provider<*>>() {
    override fun serialize(value: Provider<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeNull()
        }
    }
}

