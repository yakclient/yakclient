package net.yakclient.components.extloader.extension.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import net.yakclient.boot.dependency.DependencyTypeContainer

public class ExtensionRepositoryFactory(
    private val dependencyProviders: DependencyTypeContainer
) : RepositoryFactory<ExtensionRepositorySettings, ExtensionArtifactRequest, SimpleMavenArtifactStub, ExtensionArtifactReference, ExtMavenArtifactRepository> {
    override fun createNew(settings: SimpleMavenRepositorySettings): ExtMavenArtifactRepository {
        return ExtMavenArtifactRepository(
            this,
            ExtensionMetadataHandler(
                settings,
                dependencyProviders
            ),
            settings
        )
    }


}

internal fun extRepositoryStubResolver(settings: ExtensionRepositorySettings) = RepositoryStubResolver<SimpleMavenRepositoryStub, SimpleMavenRepositorySettings> { stub ->
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
}

// Kinda duplicate code from maven... only necessary part is the repository stub resolver, rest is for type info
public class ExtMavenArtifactRepository(
    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, ExtensionArtifactStub, ExtensionArtifactReference, ExtMavenArtifactRepository>,
    override val handler: ExtensionMetadataHandler,
    settings: SimpleMavenRepositorySettings,
) : ArtifactRepository<ExtensionArtifactRequest, ExtensionArtifactStub, ExtensionArtifactReference> {
    override val name: String = "ext"

    override val stubResolver: ArtifactStubResolver<SimpleMavenRepositoryStub, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<ExtensionArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>> = object : ArtifactStubResolver<SimpleMavenRepositoryStub, ExtensionArtifactStub, ExtensionArtifactReference> {
        override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, ExtensionArtifactStub, ExtensionArtifactReference, ExtMavenArtifactRepository> = this@ExtMavenArtifactRepository.factory

        override val repositoryResolver: RepositoryStubResolver<SimpleMavenRepositoryStub, SimpleMavenRepositorySettings> =
            extRepositoryStubResolver(settings)

        override fun resolve(stub: ExtensionArtifactStub): Either<ArtifactException, ExtensionArtifactReference> = either.eager {
            val localSettings = stub.candidates
                .map(repositoryResolver::resolve)
                .map { it.bind() }

            val repositories = localSettings.map(factory::createNew)

            val bind = repositories
                .map { it.get(stub.request) }
                .firstOrNull(Either<*, *>::isRight)?.bind()

            bind
                ?: shift(ArtifactException.ArtifactNotFound(stub.request.descriptor, repositories.map { it.name }))
        }

    }

    override fun get(request: ExtensionArtifactRequest): Either<ArtifactException, ExtensionArtifactReference> = either.eager {
        val metadata = handler.requestMetadata(request.descriptor).bind()

        val children = run {
            val rawChildren = metadata.children

            val transitiveChildren = if (request.isTransitive) rawChildren else listOf()

            val scopedChildren = transitiveChildren.filter { request.includeScopes.contains(it.scope) }

            val allowedChildren =
                scopedChildren.filterNot { request.excludeArtifacts.contains(it.descriptor.artifact) }

            allowedChildren.map {
                SimpleMavenArtifactStub(
                    request.withNewDescriptor(it.descriptor),
                    it.candidates
                )
            }
        }

        ExtensionArtifactReference(
            metadata,
            children
        )
    }
}