package net.yakclient.components.extloader.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerProcess
import net.yakclient.common.util.immutableLateInit
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.components.extloader.extension.archive.ExtensionArchiveHandle

public data class ExtensionProcess(
    val ref: ExtensionReference,
    private val context: ExtensionContext
) : ContainerProcess {
    override val archive: ExtensionArchiveHandle
        get() = ref.archive

    override fun start(): Unit = ref.extension.init()
}

public data class ExtensionReference(
    private val lazyLoader: (minecraft: ArchiveHandle) -> Pair<Extension, ExtensionArchiveHandle>
) {
    public var extension : Extension by immutableLateInit()
    public var archive: ExtensionArchiveHandle by immutableLateInit()

    public fun supplyMinecraft(handle: ArchiveHandle) {
        val (e, a) = lazyLoader(handle)
        extension = e
        archive = a
    }
}