@file:ImplementFeatures

package dev.extframework.test.extension

import dev.extframework.core.api.feature.Feature
import dev.extframework.core.api.feature.ImplementFeatures
import dev.extframework.test.app.BlackboxApp

@Feature
public fun initApp() {
    BlackboxApp().main()
}