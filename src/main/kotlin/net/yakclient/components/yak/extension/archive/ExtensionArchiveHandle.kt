package net.yakclient.components.yak.extension.archive

import net.yakclient.archives.ArchiveHandle

public interface ExtensionArchiveHandle : ArchiveHandle {
    public val reference: ExtensionArchiveReference
}