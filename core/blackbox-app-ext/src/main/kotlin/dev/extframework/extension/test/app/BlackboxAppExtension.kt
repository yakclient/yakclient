package dev.extframework.extension.test.app

import dev.extframework.core.api.Extension


public class BlackboxAppExtension : Extension() {
    override fun init() {
        println("Black box app ext init")
    }
}