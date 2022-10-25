package net.yakclient.plugins.yakclient.test.extension.artifact

import arrow.core.Either
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.yakclient.archives.Archives
import net.yakclient.boot.createMaven
import net.yakclient.boot.maven.MavenDependencyGraph
import net.yakclient.boot.security.Privilege
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.plugins.yakclient.extension.ExtensionGraph
import net.yakclient.plugins.yakclient.extension.artifact.ExtensionArtifactRequest
import net.yakclient.plugins.yakclient.extension.artifact.ExtensionRepositorySettings
import net.yakclient.plugins.yakclient.test.extension.TestExtensionStore
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test

class ExtensionArtifactTests {
    fun `Setup local repository`(): Path {
        val tmpDirPath = Path.of(System.getProperty("java.io.tmpdir"))

        val repoPath = tmpDirPath resolve "repository"

        val ermPath =
            repoPath resolve "net" resolve "yakclient" resolve "example" resolve "example-extension" resolve "1.0-SNAPSHOT" resolve "example-extension-1.0-SNAPSHOT-erm.json"

        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        ermPath.make()
        ermPath.writeBytes(mapper.writeValueAsBytes(TestExtensionStore().`Generate test erm stub`()))

        return repoPath
    }

    @Test
    fun `Test extensionGraph read`() {
        val path = `Setup local repository`()

       val dependencyGraph: MavenDependencyGraph = createMaven(path.toString()) { populateFrom ->
           val mavenCentral = SimpleMaven.createContext(
               SimpleMavenRepositorySettings.mavenCentral(
                   preferredHash = HashType.SHA1
               )
           )

           val yakCentral = SimpleMaven.createContext(
               SimpleMavenRepositorySettings.default(
                   "http://repo.yakclient.net/snapshots",
                   preferredHash = HashType.SHA1
               )
           )

           val mavenLocal = SimpleMaven.createContext(
               SimpleMavenRepositorySettings.local()
           )

           mavenCentral.populateFrom("io.arrow-kt:arrow-core:1.1.2")
           mavenCentral.populateFrom("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
           mavenCentral.populateFrom("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

           mavenLocal.populateFrom("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT")
           mavenLocal.populateFrom("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT")

           mavenCentral.populateFrom("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
           mavenCentral.populateFrom("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")

           yakCentral.populateFrom("net.yakclient:common-util:1.0-SNAPSHOT")
           yakCentral.populateFrom("net.yakclient:archives:1.0-SNAPSHOT")
       }

        val graph = ExtensionGraph(
            path,
            Archives.Finders.JPM_FINDER,
            Archives.Resolvers.JPM_RESOLVER,
            PrivilegeManager(
                null,
                PrivilegeAccess.emptyPrivileges(),
            ),
            ClassLoader.getSystemClassLoader()
        )

        val either = graph.loaderOf(ExtensionRepositorySettings.local(
            path.toString(),
        )).load(ExtensionArtifactRequest("net.yakclient.example:example-extension:1.0-SNAPSHOT"))

         if (!either.isRight())  throw (either as Either.Left).value

        println(either.orNull())
    }
}