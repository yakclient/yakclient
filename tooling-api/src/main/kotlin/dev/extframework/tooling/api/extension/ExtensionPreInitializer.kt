package dev.extframework.tooling.api.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata.Descriptor
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.jobs.async.AsyncJob
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey

public interface ExtensionPreInitializer : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = ExtensionPreInitializer

    public fun preInit(node: ExtensionNode, actions: Actions) : AsyncJob<Unit>

    public companion object : EnvironmentAttributeKey<ExtensionPreInitializer>

    public interface Actions {
        public fun <D: Descriptor, T: ArtifactRequest<D>, S: RepositorySettings> addRequest(
            request: T,
            repository: S,
            resolver: ArchiveNodeResolver<D, T, *, S, *>
        )
    }
}