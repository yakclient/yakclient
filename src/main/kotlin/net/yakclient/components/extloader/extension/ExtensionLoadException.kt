package net.yakclient.components.extloader.extension

import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveTrace
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor

public class ExtensionLoadException(
    descriptor: ExtensionDescriptor,
    cause: Throwable
) : ArchiveException(ArchiveTrace(descriptor,null), "Error loading extension", cause)