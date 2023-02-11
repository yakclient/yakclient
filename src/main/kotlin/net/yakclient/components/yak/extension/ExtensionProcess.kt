package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerProcess
import net.yakclient.common.util.immutableLateInit
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext

public data class ExtensionProcess(
    val ref: ExtensionReference,
    private val context: ExtensionContext
) : ContainerProcess {
    override val archive: ArchiveHandle
        get() = ref.archive

    override fun start(): Unit = ref.extension.init(context)
}

public data class ExtensionReference(
    private val lazyLoader: (minecraft: ArchiveHandle) -> Pair<Extension, ArchiveHandle>
) {
    public var extension : Extension by immutableLateInit()
    public var archive: ArchiveHandle by immutableLateInit()

    public fun supplyMinecraft(handle: ArchiveHandle) {
        val (e, a) = lazyLoader(handle)
        extension = e
        archive = a
    }
}