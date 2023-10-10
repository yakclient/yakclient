package net.yakclient.components.extloader.tweaker

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.io.path.writeBytes

public class ExtToTweakerDataAccess(
    path: Path,
) : DataAccess<SimpleMavenDescriptor, List<SimpleMavenDescriptor>> {
    private val path = path resolve "tweakers.json"
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    override fun read(key: SimpleMavenDescriptor): List<SimpleMavenDescriptor>? {
        return mapper.readValue<Map<SimpleMavenDescriptor, List<SimpleMavenDescriptor>>>(path.toFile())[key]
    }

    override fun write(key: SimpleMavenDescriptor, value: List<SimpleMavenDescriptor>) {
        val map = mapper.readValue<MutableMap<SimpleMavenDescriptor, List<SimpleMavenDescriptor>>>(
            path.toFile()
        )
        map[key] = value

        path.writeBytes(mapper.writeValueAsBytes(map))
    }
}