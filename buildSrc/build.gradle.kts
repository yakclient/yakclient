plugins {
    `kotlin-dsl`
    id("dev.extframework.common") version "1.0.10"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

common {
    defaultJavaSettings()
}

