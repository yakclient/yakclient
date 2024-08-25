package dev.extframework.test.extension

import dev.extframework.core.api.Extension

public class TestLinkExtension : Extension() {
    override fun init() {
        println("Test link extension initializing")
        initApp()
    }
}