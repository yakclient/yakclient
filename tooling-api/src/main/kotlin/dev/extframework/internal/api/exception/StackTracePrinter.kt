package dev.extframework.internal.api.exception

import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey
import java.io.PrintWriter

public interface StackTracePrinter : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = StackTracePrinter
    public fun printStacktrace(throwable: Throwable, printer: PrintWriter)

    public companion object : EnvironmentAttributeKey<StackTracePrinter>
}