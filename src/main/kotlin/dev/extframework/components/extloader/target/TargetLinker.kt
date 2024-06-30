package dev.extframework.components.extloader.target

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.boot.archive.ArchiveRelationship
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.loader.*
import dev.extframework.components.extloader.api.environment.EnvironmentAttribute
import dev.extframework.components.extloader.api.environment.EnvironmentAttributeKey
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public class TargetLinker(
//    environment: ExtLoaderEnvironment,
    targetDescriptor: SimpleMavenDescriptor,
    private val target: ClassProvider,// by lazy {  ArchiveClassProvider(app.handle) }
    private val targetResources: ResourceProvider,// by lazy {  ArchiveResourceProvider(app.handle) }

    private val misc: MutableClassProvider = MutableClassProvider(ArrayList()),
    private val miscResources: MutableResourceProvider = MutableResourceProvider(ArrayList()),
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = TargetLinker

    private var clState: MutableSet<String> = HashSet()
    private var rlState: LinkerState = LinkerState.NEITHER
    private val clLock: ReentrantLock = ReentrantLock()
    private val rlLock: ReentrantLock = ReentrantLock()

//    private val app = environment[ApplicationTarget]!!.reference
//    private val target: ClassProvider by lazy {  ArchiveClassProvider(app.handle) }
//    private val targetResources: ResourceProvider by lazy {  ArchiveResourceProvider(app.handle) }

    public val targetTarget: ArchiveTarget = ArchiveTarget(
        targetDescriptor,
        object : ArchiveRelationship {
            override val name: String = "Circular - Target"
            override val classes: ClassProvider = object : ClassProvider {
                override val packages: Set<String> by lazy { target.packages }

                override fun findClass(name: String): Class<*>? {
                    return findClassInternal(name, LinkerState.LOADING_TARGET)
                }
            }
            override val resources: ResourceProvider = object : ResourceProvider {
                override fun findResources(name: String): Sequence<URL> {
                    return findResourceInternal(name, LinkerState.LOADING_TARGET)
                }
            }
        }
    )

    public val miscTarget: ArchiveTarget = ArchiveTarget(
        SimpleMavenDescriptor("dev.extframework.components", "ext-loader", "current", "misc"),
        object : ArchiveRelationship {
            override val name: String = "Circular - Misc"
            override val classes: ClassProvider = object : ClassProvider {
                override val packages: Set<String> = HashSet()

                override fun findClass(name: String): Class<*>? {
                    return findClassInternal(name, LinkerState.LOADING_MISC)
                }
            }
            override val resources: ResourceProvider = object : ResourceProvider {
                override fun findResources(name: String): Sequence<URL> {
                    return findResourceInternal(name, LinkerState.LOADING_MISC)
                }
            }
        }
    )

    public val targetName: String by targetDescriptor::name

    public fun addMiscClasses(provider: ClassProvider): Unit = misc.add(provider)
    public fun addMiscResources(provider: ResourceProvider): Unit = miscResources.add(provider)

    // Dont need the same approach as class loading because loading one resource should never trigger the loading of another.
    private fun findResourceInternal(name: String, state: LinkerState): Sequence<URL> {
        return rlLock.withLock {
            if (
                (this.rlState == LinkerState.LOADING_TARGET && state == LinkerState.LOADING_MISC) ||
                (this.rlState == LinkerState.LOADING_MISC && state == LinkerState.LOADING_TARGET)
            ) {
                this.rlState = LinkerState.NEITHER
                return emptySequence()
            }

            this.rlState = state
            val r = when (state) {
                LinkerState.LOADING_TARGET -> targetResources.findResources(name)
                LinkerState.LOADING_MISC -> miscResources.findResources(name)
                LinkerState.NEITHER -> throw IllegalArgumentException("Cannot load linker state of neither.")
            }

            this.rlState = LinkerState.NEITHER

            r
        }
    }

    private fun findClassInternal(name: String, state: LinkerState): Class<*>? {
        return clLock.withLock {
            if (
                clState.contains(name)
            ) {
                this.clState.clear()
                throw ClassNotFoundException()
            }

            this.clState.add(name)

            val c = when (state) {
                LinkerState.LOADING_TARGET -> target.findClass(name)
                LinkerState.LOADING_MISC -> misc.findClass(name)
                LinkerState.NEITHER -> throw IllegalArgumentException("Cannot load linker state of 'NEITHER'.")
            }

            this.clState.remove(name)

            c
        }
    }

    public companion object : EnvironmentAttributeKey<TargetLinker>

    private enum class LinkerState {
        NEITHER,
        LOADING_TARGET,
        LOADING_MISC
    }

//    private inner class TargetResourceProvider : SourceProvider {
//        override val packages: Set<String> = HashSet()
//
//        override fun getResource(name: String): URL? = findResourceInternal(name, LinkerState.LOADING_TARGET)
//
//        override fun getResource(name: String, module: String): URL? = getResource(name)
//
//        override fun getSource(name: String): ByteBuffer? = null
//    }
//
//    private inner class MiscResourceProvider : SourceProvider {
//        override val packages: Set<String> = HashSet()
//
//        override fun getResource(name: String): URL? = findResourceInternal(name, LinkerState.LOADING_MISC)
//
//        override fun getResource(name: String, module: String): URL? = getResource(name)
//
//        override fun getSource(name: String): ByteBuffer? = null
//
//    }
//
//    private inner class TargetClassProvider : ClassProvider by target {
//        override fun findClass(name: String): Class<*>? = findClassInternal(name, LinkerState.LOADING_TARGET)
//    }
//
//    private inner class MiscClassProvider : ClassProvider by misc {
//        override fun findClass(name: String): Class<*>? = findClassInternal(name, LinkerState.LOADING_MISC)
//    }


}