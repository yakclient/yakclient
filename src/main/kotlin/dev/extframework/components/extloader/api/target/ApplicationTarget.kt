package dev.extframework.components.extloader.api.target

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.archives.mixin.MixinInjection
import dev.extframework.components.extloader.api.environment.EnvironmentAttribute
import dev.extframework.components.extloader.api.environment.EnvironmentAttributeKey
import dev.extframework.minecraft.bootstrapper.MinecraftClassTransformer

public interface ApplicationTarget : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ApplicationTarget

    public val reference: AppArchiveReference

    public fun mixin(destination: String, transformer: MinecraftClassTransformer)

    public fun mixin(destination: String, priority: Int, transformer: MinecraftClassTransformer)

    public fun start(args: Array<String>)

    public fun end()

    public companion object : EnvironmentAttributeKey<ApplicationTarget>
}

public interface AppArchiveReference {
    public val reference: ArchiveReference
    public val dependencyReferences: List<ArchiveReference>
    public val descriptor: SimpleMavenDescriptor

    public val handle: ArchiveHandle
    public val dependencyHandles: List<ArchiveHandle>


    public val handleLoaded: Boolean

    public fun load(parent: ClassLoader): Job<Unit>
}

public interface MixinTransaction {
    public data class Metadata<T : MixinInjection.InjectionData>(
        val destination: String, // Dot format, ie org.example.ClassA
        val data: T,
        val injection: MixinInjection<T>
    )
}
