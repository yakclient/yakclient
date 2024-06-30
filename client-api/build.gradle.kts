version = "1.0-SNAPSHOT"

group = "dev.extframework"
version = "1.2-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    jvmArgs = listOf(
        "--add-reads",
        "kotlin.stdlib=kotlinx.coroutines.core.jvm"
    )
}

common {
    publishing {
        publication {
            artifactId = "client-api"

            pom {
                name.set("YakClient Client API")
                description.set("YakClients Client API")
                url.set("https://github.com/extframework/ext-loader")
            }
        }
    }
}