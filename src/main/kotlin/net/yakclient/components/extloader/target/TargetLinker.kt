package net.yakclient.components.extloader.target

import net.yakclient.boot.loader.ClassProvider
import net.yakclient.boot.loader.MutableClassProvider
import net.yakclient.boot.loader.MutableSourceProvider
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public class TargetLinker(
    private val target: ClassProvider,
    private val targetSource: SourceProvider,

    private val misc: MutableClassProvider = MutableClassProvider(ArrayList()),
    private val miscSource: MutableSourceProvider = MutableSourceProvider(ArrayList()),
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = TargetLinker

    private var clState: MutableSet<String> = HashSet()
    private var rlState: LinkerState = LinkerState.NEITHER
    private val clLock: ReentrantLock = ReentrantLock()
    private val rlLock: ReentrantLock = ReentrantLock()

    public val targetClassProvider: ClassProvider = TargetClassProvider()
    public val miscClassProvider: ClassProvider = MiscClassProvider()

    public val targetSourceProvider: SourceProvider = TargetResourceProvider()
    public val miscSourceProvider : SourceProvider = MiscResourceProvider()

    public fun addMiscClasses(provider: ClassProvider): Unit = misc.add(provider)
    public fun addMiscSources(provider: SourceProvider): Unit = miscSource.add(provider)


    // Dont need the same approach as class loading because loading one resource should never trigger the loading of another.
    private fun findResourceInternal(name: String, state: LinkerState): URL? {
        return rlLock.withLock {
            if (
                    (this.rlState == LinkerState.LOADING_TARGET && state == LinkerState.LOADING_MISC) ||
                    (this.rlState == LinkerState.LOADING_MISC && state == LinkerState.LOADING_TARGET)
            ) {
                this.rlState = LinkerState.NEITHER
                return null
            }

            this.rlState = state
            val r = when (state) {
                LinkerState.LOADING_TARGET -> targetSource.getResource(name)
                LinkerState.LOADING_MISC -> miscSource.getResource(name)
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

    private inner class TargetResourceProvider : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun getResource(name: String): URL? = findResourceInternal(name, LinkerState.LOADING_TARGET)

        override fun getResource(name: String, module: String): URL? = getResource(name)

        override fun getSource(name: String): ByteBuffer? = null
    }

    private inner class MiscResourceProvider : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun getResource(name: String): URL? = findResourceInternal(name, LinkerState.LOADING_MISC)

        override fun getResource(name: String, module: String): URL? = getResource(name)

        override fun getSource(name: String): ByteBuffer? = null

    }

    private inner class TargetClassProvider : ClassProvider by target {
        override fun findClass(name: String): Class<*>? = findClassInternal(name, LinkerState.LOADING_TARGET)
    }

    private inner class MiscClassProvider : ClassProvider by misc {
        override fun findClass(name: String): Class<*>? = findClassInternal(name, LinkerState.LOADING_MISC)
    }


}