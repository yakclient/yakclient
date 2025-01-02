import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.publish.ExtensionPublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import publish.BuildBundle
import publish.GenerateMetadata

group = "dev.extframework.extension"
version = "1.0.18-BETA"

sourceSets {
    create("tweaker")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    "tweakerImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    "tweakerImplementation"(project(":tooling-api"))
    "tweakerImplementation"(project(":core:core-api"))
    "tweakerImplementation"(project(":core"))
    "tweakerImplementation"(partition(":core", "tweaker"))
    "tweakerImplementation"(project("core-mc-api"))

    launcherMetaHandler(configurationName = "tweakerImplementation")
    boot(configurationName = "tweakerImplementation", )
    jobs(configurationName = "tweakerImplementation")
    artifactResolver(configurationName = "tweakerImplementation")
    archives(configurationName = "tweakerImplementation", mixin = true)
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

val generateDevTweakerPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("tweaker")
    includeMavenLocal = true
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

val generateMetadata by tasks.registering(GenerateMetadata::class) {
    metadata {
        name.set("ExtFramework Core Minecraft")
        developers.add("extframework")
        description.set("The core library for doing all Minecraft related things with extensions.")
        app.set("minecraft")
    }
}

val generateErm by tasks.registering(GenerateErm::class) {
    partitions {
        add(generateMainPrm)
        add(generateTweakerPrm)
    }
    parents {
        add(project(":core"))
    }
}

val generateDevErm by tasks.registering(GenerateErm::class) {
    partitions {
        add(generateMainPrm)
        add(generateTweakerPrm)
    }
    parents {
        add(project(":core"))
    }
    includeMavenLocal = true
    outputFile.set(project.layout.buildDirectory.file("libs/erm-dev.json"))
}

val buildBundle by tasks.registering(BuildBundle::class) {
    partition("main") {
        jar(tasks.jar)
        prm(generateMainPrm)
    }
    partition("tweaker") {
        jar(tweakerJar)
        prm(generateTweakerPrm)
    }

    erm.from(generateErm)
    metadata.from(generateMetadata)
}

val publishExtension by tasks.registering(ExtensionPublishTask::class) {
    bundle.set(buildBundle.map { it.bundlePath })
}

tasks.withType<PublishToMavenRepository>().configureEach {
    isEnabled = false
}

task("sourcesMainJar", org.gradle.jvm.tasks.Jar::class) {
    archiveClassifier.set("sourcesMain")
    from(project.extensions.getByType(SourceSetContainer::class.java).getByName("main").allSource)
}

task("sourcesTweakerJar", org.gradle.jvm.tasks.Jar::class) {
    archiveClassifier.set("sourcesTweaker")
    from(project.extensions.getByType(SourceSetContainer::class.java).getByName("tweaker").allSource)
}

common {
    publishing {
        publication {
            artifact(generateDevErm).classifier = "erm"
            artifact(generateMainPrm).classifier = "main"
            artifact(generateDevTweakerPrm).classifier = "tweaker"
            artifact(tasks.jar).classifier = "main"
            artifact(tweakerJar).classifier = "tweaker"
        }
    }
}

publishing {
    repositories {
        maven {
//            url = uri("https://repo.extframework.dev")
            url = uri("http://127.0.0.1:6969")
            credentials {
                password = project.properties["creds.ext.key"] as? String
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