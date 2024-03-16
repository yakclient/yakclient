package net.yakclient.components.extloader.util

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.boot.archive.ArchiveRelationship
import net.yakclient.boot.archive.ArchiveTarget
import net.yakclient.boot.loader.ClassProvider
import net.yakclient.boot.loader.ResourceProvider
import net.yakclient.components.extloader.api.environment.DeferredValue
import net.yakclient.components.extloader.api.environment.extract
import net.yakclient.components.extloader.api.environment.getOrNull
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import java.io.InputStream
import java.net.URL
import java.util.*
import java.util.stream.Stream

public fun DeferredValue<out ClassLoader>.defer(): ClassLoader = object : ClassLoader() {
    override fun getResource(name: String): URL? = extract().getResource(name)

    override fun getResourceAsStream(name: String): InputStream? = extract().getResourceAsStream(name)

    override fun getResources(name: String): Enumeration<URL>? = extract().getResources(name)

    override fun loadClass(name: String): Class<*> = extract().loadClass(name)

    override fun getName(): String {
        return getOrNull()?.name ?: "uninitialized"
    }

    override fun resources(name: String?): Stream<URL> = extract().resources(name)
}

public fun DeferredValue<ArchiveTarget>.defer(descriptor: ArtifactMetadata.Descriptor) : ArchiveTarget {
    return  ArchiveTarget(
        descriptor,
        object: ArchiveRelationship {
            override val classes: ClassProvider
                get() = extract().relationship.classes
            override val name: String
                get() = extract().relationship.name
            override val resources: ResourceProvider
                get() = extract().relationship.resources

        }
    )
}