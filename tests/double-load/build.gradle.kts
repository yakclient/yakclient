import dev.extframework.gradle.common.dm.jobs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dev.extframework.extension"

sourceSets {
    create("tweaker")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":tooling-api"))
    jobs()
}

val generateTweakerPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("tweaker")
    prm.set(
        PartitionRuntimeModel(
            "tweaker", "tweaker",
            options = mutableMapOf(
                "tweaker-class" to "dev.extframework.extension.test.DoubleLoadTweaker"
            )
        )
    )
}

val generate1_0Erm by tasks.registering(GenerateErm::class) {
    version = "1.0"
    partitions {
        add(generateTweakerPrm)
    }
    outputFile.set(project.layout.buildDirectory.file("libs/erm1.0.json"))

}

val generate1_1Erm by tasks.registering(GenerateErm::class) {
    version = "1.1"
    partitions {
        add(generateTweakerPrm)
    }
    outputFile.set(project.layout.buildDirectory.file("libs/erm1.1.json"))
}

publishing {
    publications {
        create<MavenPublication>("1.0") {
            version = "1.0"
            artifact(generate1_0Erm).classifier = "erm"
            artifact(generateTweakerPrm).classifier = "tweaker"

            artifact(tasks.jar).classifier = "tweaker"
        }
        create<MavenPublication>("1.1") {
            version = "1.1"
            artifact(generate1_1Erm).classifier = "erm"
            artifact(generateTweakerPrm).classifier = "tweaker"
            artifact(tasks.jar).classifier = "tweaker"
        }
    }
}

tasks.withType<PublishToMavenRepository>() {
    enabled = false
}

tasks.named<KotlinCompile>("compileTweakerKotlin") {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
}

kotlin {
    explicitApi()
}