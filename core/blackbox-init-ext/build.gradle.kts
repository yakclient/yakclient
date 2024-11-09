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
}

val tweakerJar by tasks.registering(Jar::class) {
    from(sourceSets.named("tweaker").get().output)
    archiveClassifier = "tweaker"
}

common {
    defaultJavaSettings()
}

fun setupErm(): Any {
    val text = project.file("src/main/resources/erm.json").readText()
    val replacedText = text.replace("<MAVEN_LOCAL>", repositories.mavenLocal().url.path)

    val temp = Files.createTempFile("core-blackbox-erm", ".json")
    temp.writeText(replacedText)

    return temp
}
tasks.withType<PublishToMavenRepository>() {
    enabled = false
}



publishing {
    publications {
        create<MavenPublication>("test") {
            artifact(setupErm()).classifier = "erm"
            artifact(generateMainPrm).classifier = "main"
            artifact(tasks.jar).classifier = "main"
            artifact(generateTweakerPrm).classifier = "tweaker"
            artifact(tweakerJar).classifier = "tweaker"
        }
    }
}


afterEvaluate {
//    tasks.named("publishCore-blackbox-init-ext-releasePublicationToMavenLocal").get().dependsOn(tweakerJar)
    tasks.named("dokkaJavadoc").get().dependsOn(project(":core").tasks.named("tweakerJar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

