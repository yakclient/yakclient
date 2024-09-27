import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.publish.ExtensionPublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import publish.BuildBundle
import publish.GenerateMetadata
import java.nio.file.Files
import kotlin.io.path.writeText

group = "dev.extframework.extension"
version = "1.0.4-SNAPSHOT"

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

    launcherMetaHandler(configurationName = "tweakerImplementation")
    boot(configurationName = "tweakerImplementation")
    jobs(configurationName = "tweakerImplementation")
    artifactResolver(configurationName = "tweakerImplementation")
    archives(configurationName = "tweakerImplementation", mixin = true, )
    archiveMapper(configurationName = "tweakerImplementation", transform = true, proguard = true)
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

fun setupErm(): Any {
    val text = project.file("src/main/resources/erm.json").readText()
    val replacedText = text.replace("<MAVEN_LOCAL>", repositories.mavenLocal().url.path)

    val temp = Files.createTempFile("core-mc-erm", ".json")
    temp.writeText(replacedText)

    return temp
}

val generateMetadata by tasks.registering(GenerateMetadata::class) {
    metadata {
        name.set("ExtFramework Core Minecraft")
        developers.add("Durgan McBroom")
        description.set("The core library for doing all Minecraft related things with extensions.")
    }
}

val buildBundle by tasks.registering(BuildBundle::class) {
    partition("main") {
        jar(tasks.jar)
        prm(generateTweakerPrm)
    }
    partition("tweaker") {
        jar(tweakerJar)
        prm(generateTweakerPrm)
    }

    erm.from(setupErm())
    metadata.from(generateMetadata.get().outputs.files)
}

val publishExtension by tasks.registering(ExtensionPublishTask::class) {
    bundle.set(buildBundle.map { it.bundlePath })
}

publishing {
    repositories {
        maven {
            url = uri("https://repo.extframework.dev")
            credentials {
                password = property("creds.ext.key") as? String
            }
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