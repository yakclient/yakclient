package dev.extframework.extension.core.target

import dev.extframework.boot.loader.*
import dev.extframework.extension.core.CoreTweaker
import dev.extframework.tooling.api.environment.DeferredValue
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.target.ApplicationTarget
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public class TargetLinker(
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = TargetLinker

    private var clState: MutableSet<String> = HashSet()
    private var rlState: LinkerState = LinkerState.NEITHER
    private val clLock: ReentrantLock = ReentrantLock()
    private val rlLock: ReentrantLock = ReentrantLock()

    internal lateinit var target: DeferredValue<ApplicationTarget>

    public val targetLoader: IntegratedLoader = IntegratedLoader(
        name = "Extension -> (Linker) -> App",
        classProvider = object : ClassProvider {
            override val packages: Set<String> by lazy {
                target.extract().node.handle!!.packages
            }

            override fun findClass(name: String): Class<*>? {
                return findClassInternal(name, LinkerState.LOAD_TARGET)
            }
        },
        resourceProvider = object : ResourceProvider {
            override fun findResources(name: String): Sequence<URL> {
                return findResourceInternal(name, LinkerState.LOAD_TARGET)
            }
        },

        // TODO replacement for platform class loader?
        parent = ClassLoader.getSystemClassLoader(),
    )

    private val extensionClasses: MutableClassProvider = MutableClassProvider(ArrayList())
    private val extensionResources: MutableResourceProvider = MutableResourceProvider(ArrayList())

    public val extensionLoader: ClassLoader = IntegratedLoader(
        name = "App -> (Linker) -> Extension",
        classProvider = object : ClassProvider {
            override val packages: Set<String> by extensionClasses::packages

            override fun findClass(name: String): Class<*>? {
                return findClassInternal(name, LinkerState.LOAD_EXTENSION)
            }
        },
        resourceProvider = object : ResourceProvider {
            override fun findResources(name: String): Sequence<URL> {
                return findResourceInternal(name, LinkerState.LOAD_EXTENSION)
            }
        },
        parent = CoreTweaker::class.java.classLoader,
    )

    public fun addExtensionClasses(provider: ClassProvider): Unit = extensionClasses.add(provider)
    public fun addExtensionResources(provider: ResourceProvider): Unit = extensionResources.add(provider)

    public fun findResources(name: String): Sequence<URL> {
        return findResourceInternal(name, LinkerState.LOAD_TARGET) +
                findResourceInternal(name, LinkerState.LOAD_EXTENSION)
    }

    // Dont need the same approach as class loading because loading one resource should never trigger the loading of another.
    private fun findResourceInternal(name: String, state: LinkerState): Sequence<URL> {
        return rlLock.withLock {
            if (
                (this.rlState == LinkerState.LOAD_TARGET && state == LinkerState.LOAD_EXTENSION) ||
                (this.rlState == LinkerState.LOAD_EXTENSION && state == LinkerState.LOAD_TARGET)
            ) {
                this.rlState = LinkerState.NEITHER
                return emptySequence()
            }

            this.rlState = state
            val r = when (state) {
                LinkerState.LOAD_TARGET -> target.extract().node.handle!!.classloader.getResources(name).asSequence()
                LinkerState.LOAD_EXTENSION -> extensionResources.findResources(name)
                LinkerState.NEITHER -> throw IllegalArgumentException("Cannot load linker state of neither.")
            }

            this.rlState = LinkerState.NEITHER

            r
        }
    }

    public fun findClass(name: String): Class<*>? {
        return findClassInternal(name, LinkerState.LOAD_TARGET)
            ?: findClassInternal(name, LinkerState.LOAD_EXTENSION)
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
                LinkerState.LOAD_TARGET -> target.extract().node.handle!!.classloader.loadClass(name)
                LinkerState.LOAD_EXTENSION -> extensionClasses.findClass(name)
                LinkerState.NEITHER -> throw IllegalArgumentException("Cannot load linker state of 'NEITHER'.")
            }

            this.clState.remove(name)

            c
        }
    }

    public companion object : EnvironmentAttributeKey<TargetLinker>

    private enum class LinkerState {
        NEITHER,
        LOAD_TARGET,
        LOAD_EXTENSION
    }
}