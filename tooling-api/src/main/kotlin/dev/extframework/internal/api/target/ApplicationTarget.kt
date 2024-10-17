package dev.extframework.internal.api.target

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.internal.api.environment.EnvironmentAttribute
import dev.extframework.internal.api.environment.EnvironmentAttributeKey
import java.nio.file.Path

public typealias ApplicationDescriptor = SimpleMavenDescriptor

public interface ApplicationTarget : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ApplicationTarget

    public val node: ClassLoadedArchiveNode<ApplicationDescriptor>
    public val path: Path

    public companion object : EnvironmentAttributeKey<ApplicationTarget>
}
