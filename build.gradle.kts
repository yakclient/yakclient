import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

plugins {
    kotlin("jvm") version "1.9.21"

    id("dev.extframework.common") version "1.0.5"
}

group = "dev.extframework.components"
version = "1.1.1-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.3"
}

dependencies {
    testImplementation(project(":"))
    implementation(project(":client-api"))

    jobs(logging = true, progressSimple = true)
    artifactResolver()

    boot(test=true)
    archives(mixin=true)
    commonUtil()
    archiveMapper(transform = true, proguard = true, tiny = true)
    objectContainer()
    launcherMetaHandler()
    minecraftBootstrapper()

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")

    testImplementation(kotlin("test"))
}

open class ListAllDependencies : DefaultTask() {
    @TaskAction
    fun listDependencies() {
        val outputFile = project.file("build/resources/test/dependencies.txt")
        // Ensure the directory for the output file exists
        outputFile.parentFile.mkdirs()
        // Clear or create the output file
        outputFile.writeText("")

        val set = HashSet<String>()

        // Process each configuration that can be resolved
        project.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            println("Processing configuration: ${configuration.name}")
            try {
                configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach { dependency ->
                    collectDependencies(dependency, set)
                }
            } catch (e: Exception) {
                println("Skipping configuration '${configuration.name}' due to resolution errors.")
            }
        }

        set.forEach {
            outputFile.appendText(it)
        }
    }

    private fun collectDependencies(dependency: ResolvedDependency, set: MutableSet<String>) {
        set.add("${dependency.moduleGroup}:${dependency.moduleName}:${dependency.moduleVersion}\n")
        dependency.children.forEach { childDependency ->
            collectDependencies(childDependency, set)
        }
    }
}

tasks.register<ListAllDependencies>("writeDependencyMetadata")

tasks.test {
    dependsOn(tasks.named("writeDependencyMetadata"))
}

tasks.named("processTestResources") {
    dependsOn("writeDependencyMetadata")
}

tasks.withType<AbstractPublishToMaven> {
    onlyIf {
        ((this is PublishToMavenLocal && publication.name == "local") ||
                (this is PublishToMavenRepository && publication.name == "ext-loader-release"))
    }
}

val createDevModel by tasks.creating {
    val out = File(project.layout.buildDirectory.asFile.get(), "tmp/component-model-dev.json")
    outputs.file(out)

    File(out.parent).mkdirs()
    val model = File("${sourceSets.main.get().resources.srcDirs.first().absoluteFile}${File.separator}component-model-dev.json")
            .inputStream()
            .readBytes()
            .let(::String)
            .replace("<MAVEN_LOCAL>", File(repositories.mavenLocal().url).toString())

    out.writeText(model)
}

common {
    publishing {
        publication {
            artifact("${sourceSets.main.get().resources.srcDirs.first().absoluteFile}${File.separator}component-model.json").classifier =
                "component-model"

            artifactId = "ext-loader"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("local") {
            from(components["java"])
            artifact(createDevModel.outputs.files.singleFile).classifier =
                    "component-model"

            artifactId = "ext-loader"
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.fabricmc.net/")
        }
        extFramework()
    }

    common {
        defaultJavaSettings()
        publishing {
            repositories {
                extFramework(credentials = propertyCredentialProvider)
            }

            publication {
                withJava()
                withSources()
                withDokka()

                commonPom {
                    packaging = "jar"

                    defaultDevelopers()
                    gnuLicense()
                    extFrameworkScm("ext-loader")
                }
            }
        }
    }

    kotlin {
        explicitApi()
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}