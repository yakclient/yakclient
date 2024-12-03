import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.publish.ExtensionPublishTask

plugins {
    kotlin("jvm") version "1.9.21"

    id("dev.extframework.common") version "1.0.38"
}

group = "dev.extframework"
version = "2.1.11-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.3"
}

dependencies {
    testImplementation(project(":"))

    jobs(logging = true, progressSimple = true)
    artifactResolver()

    boot()
    archives(mixin = true)
    commonUtil()
    objectContainer()

    implementation(project(":tooling-api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    testImplementation(kotlin("test"))
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.17")
}

common {
    publishing {
        publication {
            artifactId = "ext-loader"
        }
    }
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

val publishExtensions by tasks.registering {
    dependsOn(project(":core").tasks.withType(ExtensionPublishTask::class))
    dependsOn(project(":core-mc").tasks.withType(ExtensionPublishTask::class))
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.fabricmc.net/")
        }
        extFramework()
        mavenLocal()
    }

    kotlin {
        explicitApi()
        jvmToolchain(8)
        compilerOptions {
            freeCompilerArgs.addAll(listOf("-Xjvm-default", "all"))
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }



    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }
}