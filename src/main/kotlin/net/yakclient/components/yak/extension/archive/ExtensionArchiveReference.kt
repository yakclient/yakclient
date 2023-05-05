package net.yakclient.components.yak.extension.archive

import net.yakclient.archives.ArchiveReference
import net.yakclient.components.yak.extension.ExtensionRuntimeModel
import net.yakclient.components.yak.extension.ExtensionVersionPartition

public interface ExtensionArchiveReference : ArchiveReference {
    public val delegate: ArchiveReference
    public val enabledPartitions: Set<ExtensionVersionPartition>
    public val erm: ExtensionRuntimeModel
}