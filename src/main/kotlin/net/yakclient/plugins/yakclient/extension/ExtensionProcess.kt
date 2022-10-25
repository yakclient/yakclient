package net.yakclient.plugins.yakclient.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerProcess

public data class ExtensionProcess(
    val extension: Extension,
    override val archive: ArchiveHandle
) : ContainerProcess {
    override fun start(): Unit = extension.init()
}