package dev.extframework.extension.core

import dev.extframework.common.util.readInputStream
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor

// ---------- RUNTIME ----------

public val THIS_DESCRIPTOR: PartitionDescriptor = run {
    val resource = StdlibTweaker::class.java.getResourceAsStream("/descriptor.txt")
        ?: throw Exception("Unable to find the extension descriptor. Is jar this packaged correctly?")

    val extensionDescriptor = ExtensionDescriptor.parseDescriptor(String(resource.readInputStream()))
    PartitionDescriptor(extensionDescriptor, "tweaker")
}

