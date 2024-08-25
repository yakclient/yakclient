import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

plugins {
    kotlin("jvm") version "1.9.21"

    id("dev.extframework.common") version "1.0.10"
}

group = "dev.extframework"
version = "2.0-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.3"
}

dependencies {
    testImplementation(project(":"))
    implementation(project(":client-api"))

    jobs(version = "1.3.1-SNAPSHOT", logging = true, progressSimple = true)
    artifactResolver(version = "1.2.2-SNAPSHOT")

    boot(version = "3.2.1-SNAPSHOT")
    archives(mixin=true)
    commonUtil()
    archiveMapper(transform = true, proguard = true, tiny = true)
    objectContainer()
    launcherMetaHandler()

    implementation(project(":tooling-api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    testImplementation(kotlin("test"))
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.17")

    minecraftBootstrapper(version = "2.0.3-SNAPSHOT", configurationName = "testImplementation")
}

common {
    publishing {
        publication {
            artifactId = "ext-loader"
        }
    }
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

    kotlin {
        explicitApi()
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
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}