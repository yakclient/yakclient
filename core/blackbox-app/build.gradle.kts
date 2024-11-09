group = "dev.extframework"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}
tasks.withType<PublishToMavenRepository>() {
    enabled = false
}