@file:ImplementFeatures

package dev.extframework.test.extension

import dev.extframework.core.api.feature.Feature
import dev.extframework.core.api.feature.ImplementFeatures

@Feature
public fun objectReturningFeature() : String {
    println("Object retuning feature being called")

    return "Strings arent primitives"
}

@Feature
public fun voidReturningFeature() {
    println("Void returning feature being called")
}

@Feature
public fun intReturningFeature() : Int {
    println("Int returning feature being called")

    return 5
}

@Feature
public fun arrayReturningFeature(): Array<Int> {
    println("Array returning feature being called")

    return arrayOf(1, 2, 3)
}

@Feature
public fun doubleReturningFeature(): Double {
    println("Double returning feature being called")

    return 10.0
}

@Feature
public fun charReturningFeature(): Char {
    println("Char returning feature being called")

    return 'Z'
}