import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files
import kotlin.io.path.writeText

group = "dev.extframework.extension"
version = "1.0-SNAPSHOT"

sourceSets {
    create("tweaker")
}

repositories {
    mavenCentral()
}

val coreTweaker = project(":core").partition("tweaker")
dependencies {
    "tweakerImplementation"(coreTweaker)
    "tweakerImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    "tweakerImplementation"(project(":tooling-api"))
    boot(configurationName = "tweakerImplementation")
    jobs(configurationName = "tweakerImplementation")
    artifactResolver(configurationName = "tweakerImplementation")
    archives(configurationName = "tweakerImplementation", mixin = true)
    commonUtil(configurationName = "tweakerImplementation")
    objectContainer(configurationName = "tweakerImplementation")

    implementation(project(":core:core-api"))

    testImplementation(project(":"))
}

val generateMainPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("main")
    prm.set(
        PartitionRuntimeModel(
            "main", "main",
            options = mutableMapOf(
                "extension-class" to "dev.extframework.extension.test.app.BlackboxAppExtension"
            )
        )
    )
}

val generateTweakerPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("tweaker")
    prm.set(
        PartitionRuntimeModel(
            "tweaker", "tweaker",
            options = mutableMapOf(
                "tweaker-class" to "dev.extframework.extension.test.app.BlackboxAppTweaker"
            )
        )
    )
}

val tweakerJar by tasks.registering(Jar::class) {
    from(sourceSets["tweaker"].output)
    archiveBaseName.set("tweaker")
}


fun setupErm(): Any {
    val text = project.file("src/main/resources/erm.json").readText()
    val replacedText = text.replace("<MAVEN_LOCAL>", repositories.mavenLocal().url.path)

    val temp = Files.createTempFile("stdlib-blackbox-erm", ".json")
    temp.writeText(replacedText)

    return temp
}

common {
    publishing {
        publication {
            artifact(setupErm()).classifier = "erm"
            artifact(generateMainPrm).classifier = "main"
            artifact(generateTweakerPrm).classifier = "tweaker"
            artifact(tasks.jar).classifier = "main"
            artifact(tweakerJar).classifier = "tweaker"
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