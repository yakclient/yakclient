import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.dm.jobs

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":tooling-api"))
    implementation(project(":"))
    boot(version = "3.4-SNAPSHOT")
    jobs()
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<PublishToMavenRepository>() {
    enabled = false
}