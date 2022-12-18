package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerSource
import net.yakclient.common.util.immutableLateInit

public abstract class Extension {
    public var container: Container<ExtensionProcess> by immutableLateInit()
    public var classloader: ClassLoader by immutableLateInit()
    public var handle: ArchiveHandle by immutableLateInit()
    private var initialized: Boolean = false

    internal fun init(handle: ArchiveHandle) {
        if (initialized) return
        initialized = true

        container = (this::class.java.protectionDomain.codeSource as ContainerSource).handle.handle as Container<ExtensionProcess>
        classloader = this::class.java.classLoader
        this.handle = handle
    }

    public abstract fun init(context: ExtensionContext)

    public abstract fun cleanup()
}