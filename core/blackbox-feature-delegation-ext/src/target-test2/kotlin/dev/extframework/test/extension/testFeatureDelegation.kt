@file:ImplementFeatures

package dev.extframework.test.extension

import dev.extframework.core.api.delegate.Delegate
import dev.extframework.core.api.feature.Feature
import dev.extframework.core.api.feature.FeatureImplementationException
import dev.extframework.core.api.feature.ImplementFeatures

@Feature
@Delegate("target-test1")
fun delegatedFeature(): Unit = throw FeatureImplementationException()
