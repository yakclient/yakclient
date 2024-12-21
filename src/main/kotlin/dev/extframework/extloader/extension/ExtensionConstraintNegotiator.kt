package dev.extframework.extloader.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.map
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.boot.constraint.Constrained
import dev.extframework.boot.constraint.ConstraintNegotiator
import dev.extframework.boot.maven.MavenConstraintNegotiator

public class ExtensionConstraintNegotiator<T : ArtifactMetadata.Descriptor>(
    override val descriptorType: Class<T>,
    private val classify: (T) -> Any,
    private val convert: (T) -> SimpleMavenDescriptor
) : ConstraintNegotiator<T> {
    private val maven = MavenConstraintNegotiator()

    override fun classify(descriptor: T): Any {
       return descriptorType.name + classify.invoke(descriptor)
    }

    override fun negotiate(
        constraints: Set<Constrained<T>>,
        trace: ArchiveTrace
    ): Job<T> {
        val conversion = HashMap<SimpleMavenDescriptor, T>()

        return maven.negotiate(constraints.mapTo(HashSet()) {
            val mavenDesc = convert(it.descriptor)
            conversion[mavenDesc] = it.descriptor

            Constrained(
                mavenDesc,
                it.type
            )
        }, trace).map {
            conversion[it]!!
        }
    }
}