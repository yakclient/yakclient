import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.publish.ExtensionPublication
import dev.extframework.gradle.publish.ExtensionPublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import publish.BuildBundle
import publish.GenerateMetadata

plugins {
}

group = "dev.extframework.extension"
version = "1.0.1-BETA"

sourceSets {
    create("tweaker")
    create("testTweaker")
}

repositories {
    mavenCentral()
}

dependencies {
    "tweakerImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    "tweakerImplementation"(project(":tooling-api"))
    "tweakerImplementation"(project("core-api"))
    boot(configurationName = "tweakerImplementation")
    jobs(configurationName = "tweakerImplementation")
    artifactResolver(configurationName = "tweakerImplementation", )
    archives(configurationName = "tweakerImplementation", mixin = true)
    archiveMapper(configurationName = "tweakerImplementation", transform = true)
    commonUtil(configurationName = "tweakerImplementation")
    objectContainer(configurationName = "tweakerImplementation")

    implementation(partition("tweaker"))
    implementation(project(":tooling-api"))
    implementation(project("core-api"))
    boot()
    jobs()
    artifactResolver()
    archives(mixin = true)
    archiveMapper(transform = true)
    commonUtil()
    objectContainer()

    "testTweakerImplementation"(partition("tweaker"))
    "testTweakerImplementation"(kotlin("test"))
    "testTweakerImplementation"("junit:junit:4.13.2")

    testImplementation(project(":"))
}

val generateMainPrm by tasks.registering(GeneratePrm::class) {
    sourceSetName.set("main")
    prm.set(
        PartitionRuntimeModel(
            "main", "main",
            options = mutableMapOf(
                "extension-class" to "dev.extframework.extension.core.CoreExtension"
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
                "tweaker-class" to "dev.extframework.extension.core.CoreTweaker"
            )
        )
    )
}

val createDescriptorResource by tasks.registering {
    val tmp = project.layout.buildDirectory.file("generated/descriptor.txt").get().asFile
    outputs.file(tmp)

    doLast {
        val descriptor = "${project.group}:${project.name}:${project.version}"

        tmp.writeText(descriptor)
    }
}

val tweakerJar by tasks.registering(Jar::class) {
    from(sourceSets["tweaker"].output)
    from(createDescriptorResource)
    archiveBaseName.set("tweaker")
}

val copyDescriptorToResources by tasks.registering(Copy::class) {
    from(createDescriptorResource)
    into(project.layout.buildDirectory.file("resources/main/descriptor.txt"))
}

kotlin.target.compilations.getByName("testTweaker").associateWith(kotlin.target.compilations.getByName("tweaker"))
val tweakerTest by tasks.registering(Test::class) {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform()
    dependsOn(copyDescriptorToResources)
}

val generateMetadata by tasks.registering(GenerateMetadata::class) {
    metadata {
        name.set("ExtFramework Core")
        developers.add("Durgan McBroom")
        description.set("The base extension for all mixin based application targeting. Defines fundamental features other extensions may or may not rely upon.")
        app.set("*")
    }
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

    erm.from(project.files("src/main/resources/erm.json"))
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
                password = project.properties.get("creds.ext.key") as? String
            }
        }
    }
}

tasks.named<KotlinCompile>("compileTweakerKotlin") {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
}

kotlin {
    explicitApi()
}