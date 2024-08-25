@file:DefineFeatures

package dev.extframework.test.extension

import dev.extframework.core.api.feature.DefineFeatures
import dev.extframework.core.api.feature.Feature
import dev.extframework.core.api.feature.FeatureImplementationException

@Feature
public fun initApp() : Unit = throw FeatureImplementationException()