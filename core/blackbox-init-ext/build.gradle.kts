import java.nio.file.Files
import kotlin.io.path.writeText

group = "dev.extframework.extension"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:core-api"))
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
        }
    }
}

afterEvaluate {
    tasks.named("dokkaJavadoc").get().dependsOn(project(":core").tasks.named("tweakerJar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

