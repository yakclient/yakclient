package dev.extframework.extloader.exception

import dev.extframework.internal.api.exception.ExceptionType

public enum class ExtLoaderExceptions : ExceptionType {
    PartitionLoadException,
    IllegalFeatureException,

    ExtensionLoadException,
    ExtensionSetupException,
    InvalidErm,

    WorkflowException,
}