import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.objectContainer

group = "dev.extframework"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    boot()
    implementation(project(":tooling-api"))
    implementation(project(":"))
    artifactResolver()
    objectContainer()

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
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
}