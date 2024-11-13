package dev.extframework.tooling.api.environment

import kotlin.test.Test

class TestExtensionEnvironment {
    data class BasicAttribute(
        val firstString: String = "first one",
        var basicString: String = "This is a string"
    ) : EnvironmentAttribute {

        init {
            println("Constructed")
        }

        companion object : EnvironmentAttributeKey<BasicAttribute>

        override val key: EnvironmentAttributeKey<*> = BasicAttribute
    }

    @Test
    fun `Test set and get works works`() {
        val env = ExtensionEnvironment()
        env.set(BasicAttribute())

        println("Getting value now")
        println(env[BasicAttribute].extract())
    }

    @Test
    fun `Test updates work`() {
        val env = ExtensionEnvironment()

        env.update(BasicAttribute) { old ->
            old.basicString = "This value was updated"
            old.copy(firstString = "Second string")
        }

        env.set(BasicAttribute())

        println("Getting value now")
        check(env[BasicAttribute].extract().basicString == "This value was updated")
        check(env[BasicAttribute].extract().firstString == "Second string")
    }
}