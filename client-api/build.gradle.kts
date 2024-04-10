version = "1.0-SNAPSHOT"

group = "net.yakclient"
version = "1.1-SNAPSHOT"

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    jvmArgs = listOf(
        "--add-reads",
        "kotlin.stdlib=kotlinx.coroutines.core.jvm"
    )
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
        create<MavenPublication>("client-api-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            artifactId = "client-api"

            pom {
                name.set("YakClient Client API")
                description.set("YakClients Client API")
                url.set("https://github.com/yakclient/yakclient")

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
                    connection.set("scm:git:git://github.com/yakclient/yakclient")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/yakclient.git")
                    url.set("https://github.com/yakclient/yakclient")
                }
            }
        }
    }
}