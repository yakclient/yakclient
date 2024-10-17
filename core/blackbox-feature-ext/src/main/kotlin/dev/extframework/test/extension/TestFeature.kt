@file:DefineFeatures

package dev.extframework.test.extension

import dev.extframework.core.api.feature.DefineFeatures
import dev.extframework.core.api.feature.Feature
import dev.extframework.core.api.feature.FeatureImplementationException

@Feature
public fun objectReturningFeature(): String = throw FeatureImplementationException()

@Feature
public fun voidReturningFeature(): Unit = throw FeatureImplementationException()

@Feature
public fun intReturningFeature(): Int = throw FeatureImplementationException()

@Feature
public fun arrayReturningFeature(): Array<Int> = throw FeatureImplementationException()

@Feature
public fun doubleReturningFeature(): Double = throw FeatureImplementationException()

@Feature
public fun charReturningFeature(): Char = throw FeatureImplementationException()

@Feature
public fun paramFeature(int: Int?): Unit = throw FeatureImplementationException()