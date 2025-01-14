package dev.extframework.test.extension

import dev.extframework.core.api.mixin.InjectionContinuation
import dev.extframework.core.api.mixin.Mixin
import dev.extframework.core.api.mixin.SourceInjection
import dev.extframework.test.app.BlackboxApp

@Mixin(BlackboxApp::class)
abstract class AppMixin {
    @SourceInjection(
        point = "after-begin",
        methodTo = "main()V"
    )
    fun `Inject system property test into main`(
        continuation: InjectionContinuation
    ) : InjectionContinuation.Result {
        System.setProperty("tests.app.mixin", "true")
        println("Mixin here")
        return continuation.resume()
    }

    @SourceInjection(
        point = "before-end",
        methodTo = "test()Ljava/lang/String;",
    )
    fun `Inject into test func`(
        continuation: InjectionContinuation,
    ) : InjectionContinuation.Result {
        return continuation.returnEarly("This is injected actually")
    }

    @SourceInjection(
        point = "before-end",
        methodTo = "primitiveTest()I",
    )
    fun `Inject into primitive test func`(
        continuation: InjectionContinuation,
    ) : InjectionContinuation.Result {
        return continuation.returnEarly(66)
    }
}