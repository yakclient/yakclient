import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.objectContainer

group = "dev.extframework"

version = "1.0-SNAPSHOT"

dependencies {
    boot()
    objectContainer()
    jobs()
    artifactResolver()
    archives(mixin = true)

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}