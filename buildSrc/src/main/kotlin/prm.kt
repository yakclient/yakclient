import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.nio.file.Paths
import java.util.ArrayList

data class ExtensionRepository(
    val type: String,
    val settings: Map<String, String>
)

data class PartitionRuntimeModel(
    var type: String,

    var name: String,

    val repositories: MutableList<ExtensionRepository> = mutableListOf(),
    val dependencies: MutableSet<Map<String, String>> = mutableSetOf(),

    val options: MutableMap<String, String> = mutableMapOf(),
)

abstract class GeneratePrm : DefaultTask() {
    @get:Input
    abstract val sourceSetName: Property<String>

    @get:Input
    val includeMavenLocal: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    @get:Internal
    abstract val prm: Property<PartitionRuntimeModel>

    @get:Input
    abstract val ignoredModules: SetProperty<String>


    @get:OutputFile
    val output: RegularFileProperty
        get() = project.objects.fileProperty()
            .convention(project.layout.buildDirectory.file(
                project.provider { "generated/${sourceSetName.get()}-prm.json" }
            ))


    @TaskAction
    fun generate() {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        val basePrm = prm.get()

        val implConfigurationName = sourceSets.getByName(sourceSetName.get()).compileClasspathConfigurationName

        val modulesToIgnore = ignoredModules.get().toMutableSet()

        val dependencyDescriptors = project.configurations
            .first { it.name == implConfigurationName }
            .resolvedConfiguration.resolvedArtifacts
            .filterNot { modulesToIgnore.remove(it.moduleVersion.id.module.toString()) }
            .map { it.moduleVersion.id.toString() }
        val dependencies = dependencyDescriptors
            .map {
                mapOf(
                    "descriptor" to it,
                    "isTransitive" to "true",
                    "includeScopes" to "compile,runtime,import",
                    "excludeArtifacts" to ""
                )
            }

        if (modulesToIgnore.isNotEmpty()) throw Exception(
            "Told to ignore the following modules however they did not " +
                    "appear in the resolved dependencies: \n" +
                    modulesToIgnore.joinToString(separator = "") { " - $it\n" } +
                    "Resolved dependencies:\n${dependencyDescriptors.joinToString(separator = "") { " - $it\n" }}" +
                    "Project: '${project.name}'"
        )

        val repositories = project.repositories
            .mapNotNullTo(ArrayList()) {
                when (it) {
                    is DefaultMavenLocalArtifactRepository -> {
                        null
                    }
                    is DefaultMavenArtifactRepository -> {
                        ExtensionRepository(
                            "simple-maven",
                            mapOf(
                                "type" to "default",
                                "location" to it.url.toString()
                            )
                        )
                    }

                    else -> throw IllegalArgumentException("Unknown repository type: '$it'")
                }
            }

        if (includeMavenLocal.get()) repositories.add(
            0,
            ExtensionRepository(
                "simple-maven",
                mapOf(
                    "type" to "local",
                    "location" to Paths.get(project.repositories.mavenLocal().url).toString()
                )
            )
        )

        basePrm.repositories.addAll(repositories)
        basePrm.dependencies.addAll(dependencies)

        val bytes = jacksonObjectMapper().writeValueAsBytes(basePrm)
        output.get().asFile.writeBytes(bytes)
    }
}