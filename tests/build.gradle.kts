import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.minecraftBootstrapper
import dev.extframework.gradle.common.objectContainer

group = "dev.extframework"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    extFramework()
}

dependencies {
    boot()
    implementation(project(":tooling-api"))
    implementation(project(":"))
    artifactResolver()
    objectContainer()
    minecraftBootstrapper()
    implementation(project(":core-mc:core-mc-api"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
tasks.compileTestKotlin {
    kotlinOptions {
        jvmTarget = "21"
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val listDependencies by tasks.registering(ListAllDependencies::class) {
    output.set(project.layout.buildDirectory.file("resources/test/dependencies.txt"))
}

tasks.test {
    useJUnitPlatform()

    dependsOn(listDependencies)

    dependsOn(project(":core").tasks.named("publishToMavenLocal"))
    dependsOn(project(":core:core-api").tasks.named("publishToMavenLocal"))

    dependsOn(project(":core:core-blackbox-app").tasks.named("jar"))
    dependsOn(project(":core:core-blackbox-init-ext").tasks.named("publishToMavenLocal"))
    dependsOn(project(":core:core-blackbox-feature-ext").tasks.named("publishToMavenLocal"))
    dependsOn(project(":core:core-blackbox-feature-delegation-ext").tasks.named("publishToMavenLocal"))
    dependsOn(project(":core:core-blackbox-link-ext").tasks.named("publishToMavenLocal"))
    dependsOn(project(":core:core-blackbox-app-ext").tasks.named("publishToMavenLocal"))
    dependsOn(project(":core-mc").tasks.named("publishToMavenLocal"))
    dependsOn(project(":tooling-api").tasks.named("publishToMavenLocal"))
    dependsOn(project(":core:core-api").tasks.named("publishToMavenLocal"))
}