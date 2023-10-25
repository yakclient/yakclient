package net.yakclient.components.extloader.extension.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.RepositoryStubResolutionException
import com.durganmcbroom.artifact.resolver.RepositoryStubResolver
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import net.yakclient.boot.dependency.DependencyTypeContainer

public class ExtensionRepositoryFactory(
    private val dependencyProviders: DependencyTypeContainer
) : RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
        return ExtMavenArtifactRepository(
            this,
            ExtensionMetadataHandler(
                settings,
                dependencyProviders
            ),
            settings
        )
    }

    private class ExtMavenArtifactRepository(
        factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository>,
        handler: SimpleMavenMetadataHandler, settings: SimpleMavenRepositorySettings,
    ) : SimpleMavenArtifactRepository(
        factory, handler, settings,
    ) {
        override val stubResolver: SimpleMavenArtifactStubResolver = SimpleMavenArtifactStubResolver(
            { stub ->
                either.eager {
                    val repo = stub.unresolvedRepository

                    val layout = when (repo.layout.lowercase()) {
                        "default" -> SimpleMavenDefaultLayout(
                            repo.url, settings.preferredHash,
                            repo.releases.enabled,
                            repo.snapshots.enabled,
                        )
                        "ext-local" -> SimpleMavenLocalLayout(
                            repo.url
                        )
                        else -> shift(RepositoryStubResolutionException("Invalid repository layout: '${repo.layout}"))
                    }

                    SimpleMavenRepositorySettings(
                        layout, settings.preferredHash, settings.pluginProvider
                    )
                }
            },
            factory
        )
    }
}
