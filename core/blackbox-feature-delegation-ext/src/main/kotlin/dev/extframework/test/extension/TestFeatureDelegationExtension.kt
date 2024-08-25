package dev.extframework.test.extension

import dev.extframework.core.api.Extension


public class TestFeatureDelegationExtension : Extension() {
    override fun init() {
        println("Testing feature delegation")

        delegatedFeature()
    }
}