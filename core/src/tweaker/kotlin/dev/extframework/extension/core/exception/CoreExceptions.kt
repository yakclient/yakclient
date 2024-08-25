package dev.extframework.extension.core.exception

import dev.extframework.internal.api.exception.ExceptionType

public enum class CoreExceptions : ExceptionType{
    MixinException,

    ExtensionInitializationException,
    ExtensionClassNotFound
}