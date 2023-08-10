
group = "net.yakclient"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(project(":client-api"))
    implementation("net.yakclient:archives:1.1-SNAPSHOT")
    implementation("net.yakclient:archives-mixin:1.1-SNAPSHOT")
    implementation("net.yakclient:archive-mapper:1.1-SNAPSHOT")
    implementation("net.yakclient:archive-mapper-transform:1.1-SNAPSHOT")
    implementation("net.yakclient:object-container:1.0-SNAPSHOT")
    compileOnly("net.yakclient:boot:1.0-SNAPSHOT") {
        isChanging = true
    }
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
        create<MavenPublication>("prod") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            groupId = "net.yakclient.extloader"
            artifactId = "internal-api"

            pom {
                packaging = "jar"

                developers {
                    developer {
                        name.set("Durgan McBroom")
                    }
                }

                withXml {
                    val repositoriesNode = asNode().appendNode("repositories")
                    val yakclientRepositoryNode = repositoriesNode.appendNode("repository")
                    yakclientRepositoryNode.appendNode("id", "yakclient")
                    yakclientRepositoryNode.appendNode("url", "http://maven.yakclient.net/snapshots")
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
