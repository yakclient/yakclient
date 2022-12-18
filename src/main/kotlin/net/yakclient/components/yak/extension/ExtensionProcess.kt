package net.yakclient.components.yak.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerProcess

public data class ExtensionProcess(
    val extension: Extension,
    override val archive: ArchiveHandle,
    private val context: ExtensionContext
) : ContainerProcess {
    override fun start(): Unit = extension.init(context)
}