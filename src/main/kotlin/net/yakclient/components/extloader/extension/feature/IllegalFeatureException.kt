package net.yakclient.components.extloader.extension.feature

import net.yakclient.components.extloader.extension.ExtensionLoadException
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor

public class IllegalFeatureException(
    override val message: String
) : Exception() {
}