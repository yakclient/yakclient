plugins {
    kotlin("jvm") version "1.9.21"

    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "net.yakclient.components"
version = "1.0-SNAPSHOT"

sourceSets {
    val main by getting
    val test by getting {
        compileClasspath += main.output
        runtimeClasspath += main.output
    }
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

tasks.wrapper {
    gradleVersion = "8.3"
}

dependencies {
    testImplementation(project(":"))
    implementation(project(":client-api"))

    implementation("net.yakclient:archive-mapper:1.2-SNAPSHOT")
    implementation("net.yakclient:archive-mapper-transform:1.2-SNAPSHOT")
    implementation("net.yakclient:archive-mapper-proguard:1.2-SNAPSHOT")

    implementation("net.yakclient:launchermeta-handler:1.0-SNAPSHOT")
    implementation("io.arrow-kt:arrow-core:1.1.2")
    implementation("net.yakclient:object-container:1.0-SNAPSHOT")
    implementation("net.yakclient:archives-mixin:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:boot:2.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:common-util:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("net.yakclient.components:minecraft-bootstrapper:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")


    implementation("com.durganmcbroom:jobs:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:jobs-logging:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:jobs-progress:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:jobs-progress-simple:1.0-SNAPSHOT") {
        isChanging = true
    }

    testImplementation(kotlin("test"))
    implementation("net.yakclient:boot-test:2.0-SNAPSHOT")
    testImplementation("net.yakclient:archive-mapper-tiny:1.2-SNAPSHOT")
}


open class ListAllDependencies : DefaultTask() {
    init {
        // Define the output file within the build directory
//        val outputFile = project.file("src/test/resources/dependencies.txt")
//        outputs.file(outputFile)
    }

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

// Register the custom task in the project
tasks.register<ListAllDependencies>("writeDependencyMetadata")// tasks.register<ListAllDependencies>("writeDependencyMetadata")

tasks.test {
    dependsOn(tasks.named("writeDependencyMetadata"))
}

tasks.named("processTestResources") {
    dependsOn("writeDependencyMetadata")
}

task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

tasks.jar {

}

tasks.withType<AbstractPublishToMaven> {
    onlyIf {
        ((this is PublishToMavenLocal && publication.name == "local") ||
                (this is PublishToMavenRepository && publication.name == "prod"))
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

publishing {
    publications {
        create<MavenPublication>("local") {
            from(components["java"])
            artifact(createDevModel.outputs.files.singleFile).classifier =
                    "component-model"

            artifactId = "ext-loader"
        }

        create<MavenPublication>("prod") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            artifact("${sourceSets.main.get().resources.srcDirs.first().absoluteFile}${File.separator}component-model.json").classifier =
                    "component-model"

            artifactId = "ext-loader"

            pom {
                name.set("YakClient Software Component for loading Yak Extensions")
                description.set("Extension Loader")
                url.set("https://github.com/yakclient/ext-loader")

                packaging = "jar"

                developers {
                    developer {
                        id.set("Chestly")
                        name.set("Durgan McBroom")
                    }
                }

                licenses {
                    license {
                        name.set("GNU General Public License")
                        url.set("https://opensource.org/licenses/gpl-license")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yakclient/ext-loader")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/ext-loader.git")
                    url.set("https://github.com/yakclient/ext-loader")
                }
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
    }

    publishing {
        repositories {
            if (!project.hasProperty("maven-user") || !project.hasProperty("maven-pass")) return@repositories

            maven {
                val repo = if (project.findProperty("isSnapshot") == "true") "snapshots" else "releases"

                isAllowInsecureProtocol = true

                url = uri("http://maven.yakclient.net/$repo")

                credentials {
                    username = project.findProperty("maven-user") as String
                    password = project.findProperty("maven-pass") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
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

    tasks.compileJava {
        destinationDirectory.set(destinationDirectory.asFile.get().resolve("main"))
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

//    tasks.compileTestJava {
//        destinationDirectory.set(destinationDirectory.asFile.get().resolve("test"))
//    }

    tasks.compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}