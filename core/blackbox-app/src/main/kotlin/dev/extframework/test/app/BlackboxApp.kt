package dev.extframework.test.app

public class BlackboxApp {
    public fun main() {
        println("Doing work... This is a big large app")

        System.setProperty("tests.app", "true")
    }

    public companion object {
        // Specifically not const.
        public val secret: String = "This is a big secret"
    }
}