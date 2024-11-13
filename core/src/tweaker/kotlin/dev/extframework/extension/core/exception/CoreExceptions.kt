package dev.extframework.extension.core.exception

import dev.extframework.tooling.api.exception.ExceptionType

public enum class CoreExceptions : ExceptionType{
    MixinException,

    ExtensionInitializationException,
    ExtensionClassNotFound
}