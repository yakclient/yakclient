import dev.extframework.gradle.common.extFramework

group = "dev.extframework"
version = "1.0.1-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

common {
    publishing {
        publication {
            withJava()
            withSources()
            artifactId = "core-api"
            commonPom {
                withExtFrameworkRepo()
                defaultDevelopers()
                gnuLicense()
                extFrameworkScm("ext-loader")
            }
        }
        repositories {
            extFramework(credentials = propertyCredentialProvider)
        }
    }
}
