package net.yakclient.components.extloader.api.target

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.minecraft.bootstrapper.MinecraftClassTransformer

public interface ApplicationTarget : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ApplicationTarget

    public val reference: AppArchiveReference

//    public fun newMixinTransaction() : MixinTransaction
    public fun mixin(destination: String, transformer: MinecraftClassTransformer)

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

    public fun load(parent: ClassLoader)
}

public interface MixinTransaction {
//    public val finished: Boolean
//
    public data class Metadata<T: MixinInjection.InjectionData>(
        val data: T,
        val injection: MixinInjection<T>
    )
//
//    public fun register(destination: String, metadata: Metadata<*>)
//
//    public fun writeAll()
}
