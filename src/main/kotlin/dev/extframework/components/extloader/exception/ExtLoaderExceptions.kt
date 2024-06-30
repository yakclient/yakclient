package dev.extframework.components.extloader.exception

import dev.extframework.components.extloader.api.exception.ExceptionType

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