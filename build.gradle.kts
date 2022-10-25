import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.7.10"
    id("org.javamodularity.moduleplugin") version "1.8.12"

    id("signing")
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.6.0"
}

group = "net.yakclient.plugins"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://maven.yakclient.net/snapshots")
    }
    maven {
        name = "Durgan McBroom GitHub Packages"
        url = uri("https://maven.pkg.github.com/durganmcbroom/artifact-resolver")
        credentials {
            username = project.findProperty("dm.gpr.user") as? String
                ?: throw IllegalArgumentException("Need a Github package registry username!")
            password = project.findProperty("dm.gpr.key") as? String
                ?: throw IllegalArgumentException("Need a Github package registry key!")
        }
    }
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation("com.durganmcbroom:event-api:1.0-SNAPSHOT")
    implementation("io.arrow-kt:arrow-core:1.1.2")

    testImplementation(kotlin("test"))
    implementation("net.yakclient:archives-mixin:1.0-SNAPSHOT")
    implementation("net.yakclient:boot:1.0-SNAPSHOT") {
        exclude(group = "com.durganmcbroom", module = "artifact-resolver")
        exclude(group = "com.durganmcbroom", module = "artifact-resolver-simple-maven")

        exclude(group = "com.durganmcbroom", module = "artifact-resolver-jvm")
        exclude(group = "com.durganmcbroom", module = "artifact-resolver-simple-maven-jvm")
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
    implementation("net.yakclient.plugins:mixin-plugin:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")

}

task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

publishing {
    publications {
        create<MavenPublication>("yakclient-plugin-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            artifact("${sourceSets.main.get().resources.srcDirs.first().absoluteFile}${File.separator}prm.json").classifier = "prm"

            artifactId = "yakclient-plugin"
            groupId = "net.yakclient.plugins"

            pom {
                name.set("YakClient Plugin")
                description.set("The YakClient plugin for the boot module.")
                url.set("https://github.com/yakclient/yakclient-plugin")

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
                    connection.set("scm:git:git://github.com/yakclient/yakclient-plugin")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/yakclient-plugin.git")
                    url.set("https://github.com/yakclient/yakclient-plugin")
                }
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    group = "net.yakclient"
    version = "1.0-SNAPSHOT"

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

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.compileJava {
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}