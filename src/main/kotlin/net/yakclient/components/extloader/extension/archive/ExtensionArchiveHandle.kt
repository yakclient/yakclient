package net.yakclient.components.extloader.extension.archive

import net.yakclient.archives.ArchiveHandle

public interface ExtensionArchiveHandle : ArchiveHandle {
    public val reference: ExtensionArchiveReference
}