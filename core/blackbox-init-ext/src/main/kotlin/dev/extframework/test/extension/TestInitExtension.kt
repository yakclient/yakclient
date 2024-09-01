package dev.extframework.test.extension

import dev.extframework.core.api.Extension


public class TestInitExtension : Extension() {
    override fun init() {
        println("Testing")
        System.setProperty("tests.init", "true")
        check(TestInitTweaker.hasTweaked) { "Hasnt tweaked?" }
    }
}