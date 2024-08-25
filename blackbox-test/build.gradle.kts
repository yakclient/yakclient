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
    boot(version = "3.2.1-SNAPSHOT")
    jobs(version = "1.3.1-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}