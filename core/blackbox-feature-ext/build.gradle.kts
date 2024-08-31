import java.nio.file.Files
import kotlin.io.path.writeText

group = "dev.extframework.extension"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

sourceSets {
    create("target-test1")
}

dependencies {
    "target-test1Implementation"(project(":core:core-api"))
    implementation(project(":core:core-api"))
}

val generateMainPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("main")
    prm.set(
        PartitionRuntimeModel(
            "main", "main",
            options = mutableMapOf(
                "extension-class" to "dev.extframework.test.extension.TestFeatureExtension"
            )
        )
    )
    ignoredModules.addAll(setOf(
    ))
}

val generateTarget1Prm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("target-test1")
    prm.set(
        PartitionRuntimeModel(
            "target", "target-test1",
        )
    )
    ignoredModules.addAll(setOf(
    ))
}

fun setupErm(): Any {
    val text = project.file("src/main/resources/erm.json").readText()
    val replacedText = text.replace("<MAVEN_LOCAL>", repositories.mavenLocal().url.path)

    val temp = Files.createTempFile("core-blackbox-erm", ".json")
    temp.writeText(replacedText)

    return temp
}

val `target-test1Jar` by tasks.registering(Jar::class) {
    from(sourceSets["target-test1"].output)
    archiveBaseName.set("target-test1")
}

publishing {
    publications {
        create<MavenPublication>("test") {
            artifact(setupErm()).classifier = "erm"

            artifact(generateMainPrm).classifier = "main"
            artifact(tasks.jar).classifier = "main"

            artifact(`target-test1Jar`).classifier = "target-test1"
            artifact(generateTarget1Prm).classifier = "target-test1"
        }
    }
}

common {
    defaultJavaSettings()
}

afterEvaluate {
    tasks.named("dokkaJavadoc").get().dependsOn(project(":core").tasks.named("tweakerJar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<PublishToMavenRepository>() {
    enabled = false
}

