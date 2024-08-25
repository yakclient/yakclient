import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSetOutput

fun Project.partition(
    path: String,
    partition: String
): SourceSetOutput {
    return project(path).extensions.getByType(SourceSetContainer::class.java).named(partition).get().output
}

fun Project.partition(
    partition: String
): SourceSetOutput {
    return extensions.getByType(SourceSetContainer::class.java).named(partition).get().output
}