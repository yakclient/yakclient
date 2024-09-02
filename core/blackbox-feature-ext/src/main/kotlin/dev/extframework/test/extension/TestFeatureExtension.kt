package dev.extframework.test.extension

import dev.extframework.core.api.Extension


public class TestFeatureExtension : Extension() {
    override fun init() {
        println("Testing")
        System.setProperty("tests.feature", "true")

        System.setProperty("tests.feature.object", objectReturningFeature())
        voidReturningFeature()
        System.setProperty("tests.feature.int", intReturningFeature().toString())
        arrayReturningFeature()
        charReturningFeature()
        doubleReturningFeature()
        paramFeature(5)
    }
}