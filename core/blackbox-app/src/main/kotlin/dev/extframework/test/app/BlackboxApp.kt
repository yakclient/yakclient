package dev.extframework.test.app

public class BlackboxApp {
    public fun main() {
        println("Doing work... This is a big large app")
        println("Im gonna call test, it says: '${test()}'")

        println("The meaning of life is ${primitiveTest()}")

        System.setProperty("tests.app", "true")
    }

    public fun test(): String {
        return "Hey how are you?"
    }

    public fun primitiveTest() : Int {
        return 42
    }

    public companion object {
        // Specifically not const.
        public val secret: String = "This is a big secret"
    }
}