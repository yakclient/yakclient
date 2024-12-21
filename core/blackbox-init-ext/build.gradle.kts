import dev.extframework.gradle.common.dm.jobs
import java.nio.file.Files
import kotlin.io.path.writeText

group = "dev.extframework.extension"
version = "1.0-SNAPSHOT"

sourceSets {
    create("tweaker")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":core:core-api"))
    implementation(partition("tweaker"))
    "tweakerImplementation"(project(":tooling-api"))
    jobs(configurationName = "tweakerImplementation")
}

val generateMainPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("main")
    prm.set(
        PartitionRuntimeModel(
            "main", "main",
            options = mutableMapOf(
                "extension-class" to "dev.extframework.test.extension.TestInitExtension"
            )
        )
    )
    ignoredModules.addAll(setOf(
    ))
    includeMavenLocal = true
}

val generateTweakerPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("tweaker")
    prm.set(
        PartitionRuntimeModel(
            "tweaker", "tweaker",
            options = mutableMapOf(
                "tweaker-class" to "dev.extframework.test.extension.TestInitTweaker"
            )
        )
    )
    ignoredModules.addAll(setOf(
    ))
    includeMavenLocal = true
}

val tweakerJar by tasks.registering(Jar::class) {
    from(sourceSets.named("tweaker").get().output)
    archiveClassifier = "tweaker"
}

common {
    defaultJavaSettings()
}

tasks.withType<PublishToMavenRepository>() {
    enabled = false
}

val generateErm by tasks.registering(GenerateErm::class) {
    partitions {
        add(generateTweakerPrm)
        add(generateMainPrm)
    }
    includeMavenLocal = true
    parents {
        add(project(":core"))
    }
}

publishing {
    publications {
        create<MavenPublication>("test") {
            artifact(generateErm).classifier = "erm"
            artifact(generateMainPrm).classifier = "main"
            artifact(tasks.jar).classifier = "main"
            artifact(generateTweakerPrm).classifier = "tweaker"
            artifact(tweakerJar).classifier = "tweaker"
        }
    }
}

afterEvaluate {
    tasks.named("dokkaJavadoc").get().dependsOn(project(":core").tasks.named("tweakerJar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

