import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dev.extframework.extensions"
version = "1.0-SNAPSHOT"

sourceSets {
    create("tweaker")
}

repositories {
    mavenCentral()
}

dependencies {
    "tweakerImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    "tweakerImplementation"(project(":tooling-api"))
    "tweakerImplementation"(project(":core:core-api"))
    "tweakerImplementation"(project(":core"))
    "tweakerImplementation"(partition(":core", "tweaker"))

    boot(configurationName = "tweakerImplementation")
    jobs(configurationName = "tweakerImplementation")
    artifactResolver(configurationName = "tweakerImplementation")
    archives(configurationName = "tweakerImplementation", mixin = true, )
    archiveMapper(configurationName = "tweakerImplementation", transform = true)
    commonUtil(configurationName = "tweakerImplementation")
    objectContainer(configurationName = "tweakerImplementation")

    implementation(partition("tweaker"))
    implementation(project(":core:core-api"))
}

val generateMainPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("main")
    prm.set(
        PartitionRuntimeModel(
            "main", "main",
            options = mutableMapOf(
                "extension-class" to "dev.extframework.extension.core.minecraft.CoreMinecraft"
            )
        )
    )
    ignoredModules.addAll()
}

val generateTweakerPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("tweaker")
    prm.set(
        PartitionRuntimeModel(
            "tweaker", "tweaker",
            options = mutableMapOf(
                "tweaker-class" to "dev.extframework.extension.core.minecraft.MinecraftCoreTweaker"
            )
        )
    )
    ignoredModules.addAll(
        "dev.extframework.extension:core"
    )
}

val tweakerJar by tasks.registering(Jar::class) {
    from(sourceSets["tweaker"].output)
    archiveBaseName.set("tweaker")
}

common {
    publishing {
        publication {
            artifact(project.file("src/main/resources/erm.json")).classifier = "erm"
            artifact(generateMainPrm).classifier = "main"
            artifact(generateTweakerPrm).classifier = "tweaker"
            artifact(tasks.jar).classifier = "main"
            artifact(tweakerJar).classifier = "tweaker"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<KotlinCompile>("compileTweakerKotlin") {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
}

kotlin {
    explicitApi()
}