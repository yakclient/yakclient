package net.yakclient.components.extloader.extension.archive

import net.yakclient.archives.ArchiveHandle
import net.yakclient.internal.api.extension.archive.ExtensionArchiveReference

public interface ExtensionArchiveHandle : ArchiveHandle {
    public val reference: ExtensionArchiveReference
}