@file:ImplementFeatures

package dev.extframework.test.extension

import dev.extframework.core.api.feature.Feature
import dev.extframework.core.api.feature.ImplementFeatures

@Feature
fun delegatedFeature() {
    println("The feature has been delegated to here")
    System.setProperty("tests.feature.delegation", "true")
}