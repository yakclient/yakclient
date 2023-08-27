package net.yakclient.components.extloader.extension

import net.yakclient.boot.loader.ClassProvider
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.components.extloader.extension.versioning.ExtensionSourceProvider
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public class MinecraftLinker(
        private val extensions: ClassProvider,
        private val minecraft: ClassProvider,
        private val extensionsSource: SourceProvider,
        private val minecraftSource: SourceProvider
) {
    private var clState: MutableSet<String> = HashSet()
    private var rlState: LinkerState = LinkerState.NEITHER
    private val clLock: ReentrantLock = ReentrantLock()
    private val rlLock: ReentrantLock = ReentrantLock()


    public val minecraftClassProvider: ClassProvider = MinecraftClassProvider()
    public val extensionClassProvider: ClassProvider = ExtensionClassProvider()

    public val minecraftSourceProvider: SourceProvider = MinecraftResourceProvider()
    public val extensionSourceProvider : SourceProvider = ExtensionResourceProvider()

    // Dont need the same approach as class loading because loading one resource should never trigger the loading of another.
    private fun findResourceInternal(name: String, state: LinkerState): URL? {
        return rlLock.withLock {
            if (
                    (this.rlState == LinkerState.LOADING_MINECRAFT && state == LinkerState.LOADING_EXTENSION) ||
                    (this.rlState == LinkerState.LOADING_EXTENSION && state == LinkerState.LOADING_MINECRAFT)
            ) {
                this.rlState = LinkerState.NEITHER
                return null
            }

            this.rlState = state
            val r = when (state) {
                LinkerState.LOADING_MINECRAFT -> minecraftSource.getResource(name)
                LinkerState.LOADING_EXTENSION -> extensionsSource.getResource(name)
                LinkerState.NEITHER -> throw IllegalArgumentException("Cannot load linker state of neither.")
            }

            this.rlState = LinkerState.NEITHER

            r
        }
    }

    // Fix this (use hashset)
    // Fix mapping issue
    // Fix resource issue (also in here)
    private fun findClassInternal(name: String, state: LinkerState): Class<*>? {
        return clLock.withLock {
            if (
//                    (this.clState == LinkerState.LOADING_MINECRAFT && state == LinkerState.LOADING_EXTENSION) ||
//                    (this.clState == LinkerState.LOADING_EXTENSION && state == LinkerState.LOADING_MINECRAFT)
                clState.contains(name)
            ) {
                this.clState.clear()
                throw ClassNotFoundException()
            }

            this.clState.add(name)

            val c = when (state) {
                LinkerState.LOADING_MINECRAFT -> minecraft.findClass(name)
                LinkerState.LOADING_EXTENSION -> extensions.findClass(name)
                LinkerState.NEITHER -> throw IllegalArgumentException("Cannot load linker state of neither.")
            }

            this.clState.remove(name)

            c
        }
    }

    private enum class LinkerState {
        NEITHER,
        LOADING_MINECRAFT,
        LOADING_EXTENSION
    }

    private inner class MinecraftResourceProvider : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun getResource(name: String): URL? = findResourceInternal(name, LinkerState.LOADING_MINECRAFT)

        override fun getResource(name: String, module: String): URL? = getResource(name)

        override fun getSource(name: String): ByteBuffer? = null
    }

    private inner class ExtensionResourceProvider : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun getResource(name: String): URL? = findResourceInternal(name, LinkerState.LOADING_EXTENSION)

        override fun getResource(name: String, module: String): URL? = getResource(name)

        override fun getSource(name: String): ByteBuffer? = null

    }

    private inner class MinecraftClassProvider : ClassProvider by minecraft {
        override fun findClass(name: String): Class<*>? = findClassInternal(name, LinkerState.LOADING_MINECRAFT)
    }

    private inner class ExtensionClassProvider : ClassProvider by extensions {
        override fun findClass(name: String): Class<*>? = findClassInternal(name, LinkerState.LOADING_EXTENSION)
    }
}