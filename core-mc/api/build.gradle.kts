import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm")
}

group = "dev.extframework"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
}

dependencies {
    boot()
    implementation(project(":tooling-api"))
}

common {
    defaultJavaSettings()
    publishing {
        repositories {
            extFramework(credentials = propertyCredentialProvider)
        }

        publication {
            withJava()
            withSources()
            withDokka()

            artifactId = "core-mc-api"

            commonPom {
                packaging = "jar"

                withExtFrameworkRepo()
                defaultDevelopers()
                gnuLicense()
                extFrameworkScm("ext-loader")
            }
        }
    }
}

kotlin {
    jvmToolchain(8)
}