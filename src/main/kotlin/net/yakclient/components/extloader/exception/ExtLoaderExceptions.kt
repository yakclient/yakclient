package net.yakclient.components.extloader.exception

import net.yakclient.components.extloader.api.exception.ExceptionType

public enum class ExtLoaderExceptions : ExceptionType {
    PartitionLoadException,
    IllegalFeatureException,

    ExtensionLoadException,
    ExtensionSetupException,
    MixinException,
    ExtensionInitializationException,

    WorkflowException,

    MinecraftResourceException,
    MinecraftBootstrapStartException,

}