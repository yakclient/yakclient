package net.yakclient.components.extloader.extension.archive

import net.yakclient.archives.ArchiveReference
import net.yakclient.components.extloader.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.extension.ExtensionVersionPartition

public interface ExtensionArchiveReference : ArchiveReference {
    public val delegate: ArchiveReference
    public val enabledPartitions: Set<ExtensionVersionPartition>
    public val erm: ExtensionRuntimeModel
}