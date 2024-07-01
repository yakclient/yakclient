package dev.extframework.components.extloader.api.exception

import dev.extframework.components.extloader.api.environment.EnvironmentAttribute
import dev.extframework.components.extloader.api.environment.EnvironmentAttributeKey
import java.io.PrintWriter

public interface StackTracePrinter : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = StackTracePrinter
    public fun printStacktrace(throwable: Throwable, printer: PrintWriter)

    public companion object : EnvironmentAttributeKey<StackTracePrinter>
}