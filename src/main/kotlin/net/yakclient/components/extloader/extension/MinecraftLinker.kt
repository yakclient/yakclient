package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.loader.ClassProvider
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public class MinecraftLinker(
    private val extensions: ClassProvider,
    private val minecraft: ClassProvider
) {
    private var state: LinkerState = LinkerState.NEITHER
    private val lock: ReentrantLock = ReentrantLock()

    public val minecraftClassProvider : ClassProvider = MinecraftClassProvider()
    public val extensionClassProvider : ClassProvider = ExtensionClassProvider()

    private fun findInternal(name: String, state: LinkerState): Class<*>? {
        return lock.withLock {
            if (
                (this.state == LinkerState.LOADING_MINECRAFT && state == LinkerState.LOADING_EXTENSION) ||
                (this.state == LinkerState.LOADING_EXTENSION && state == LinkerState.LOADING_MINECRAFT)
            ) {
                this.state = LinkerState.NEITHER
                throw ClassNotFoundException()
            }

            this.state = state

            val c = when (state) {
                LinkerState.LOADING_MINECRAFT -> minecraft.findClass(name)
                LinkerState.LOADING_EXTENSION -> extensions.findClass(name)
                LinkerState.NEITHER -> throw IllegalArgumentException("Cannot load linker state of neither.")
            }

            this.state = LinkerState.NEITHER

            c
        }
    }

    private enum class LinkerState {
        NEITHER,
        LOADING_MINECRAFT,
        LOADING_EXTENSION
    }


    private inner class MinecraftClassProvider : ClassProvider by minecraft {
        override fun findClass(name: String): Class<*>? = findInternal(name, LinkerState.LOADING_MINECRAFT)
    }

    private inner class ExtensionClassProvider : ClassProvider by extensions {
        override fun findClass(name: String): Class<*>? = findInternal(name, LinkerState.LOADING_EXTENSION)
    }
}