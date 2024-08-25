import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dev.extframework.extensions"
version = "1.0-SNAPSHOT"

sourceSets {
    create("tweaker") {

    }
}

repositories {
    mavenCentral()
}

val coreTweakerOutput = project(":core").sourceSets["tweaker"].output

dependencies {
    "tweakerImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    "tweakerImplementation"(project(":tooling-api"))
    "tweakerImplementation"(project(":core:core-api"))
    "tweakerImplementation"(project(":core"))

    "tweakerImplementation"(partition(":core", "tweaker"))

    "tweakerImplementation"(coreTweakerOutput)
    boot(version = "3.2.1-SNAPSHOT",configurationName = "tweakerImplementation")
    jobs(version = "1.3.1-SNAPSHOT",configurationName = "tweakerImplementation")
    artifactResolver(configurationName = "tweakerImplementation", version = "1.2.2-SNAPSHOT")
    archives(configurationName = "tweakerImplementation", mixin = true, )
    archiveMapper(version = "1.2.4-SNAPSHOT", configurationName = "tweakerImplementation", transform = true)
    commonUtil(configurationName = "tweakerImplementation")
    objectContainer(configurationName = "tweakerImplementation")

    implementation(sourceSets.getByName("tweaker").output)
    implementation(project(":tooling-api"))
    boot(version = "3.2.1-SNAPSHOT")
    jobs(version = "1.3.1-SNAPSHOT",)
    artifactResolver()
    archives(mixin = true)
    archiveMapper(version = "1.2.4-SNAPSHOT", transform = true)
    commonUtil()
    objectContainer()
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