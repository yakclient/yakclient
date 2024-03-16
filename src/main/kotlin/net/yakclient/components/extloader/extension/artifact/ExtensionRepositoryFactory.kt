package net.yakclient.components.extloader.extension.artifact

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactStub
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositoryStub
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
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

internal fun extRepositoryStubResolver(settings: ExtensionRepositorySettings) =
    RepositoryStubResolver<SimpleMavenRepositoryStub, SimpleMavenRepositorySettings> { stub ->
        result {
            val repo = stub.unresolvedRepository

            val layout = when (repo.layout.lowercase()) {
                "default" -> SimpleMavenDefaultLayout(
                    repo.url, settings.preferredHash,
                    repo.releases.enabled,
                    repo.snapshots.enabled,
                    settings.requireResourceVerification,
                )

                "ext-local" -> SimpleMavenLocalLayout(
                    repo.url
                )

                else -> throw RepositoryStubResolutionException("Invalid repository layout: '${repo.layout}")
            }

            SimpleMavenRepositorySettings(
                layout, settings.preferredHash, settings.pluginProvider, settings.requireResourceVerification
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

    override val stubResolver: ArtifactStubResolver<SimpleMavenRepositoryStub, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<ExtensionArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>> =
        object : ArtifactStubResolver<SimpleMavenRepositoryStub, ExtensionArtifactStub, ExtensionArtifactReference> {
            override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, ExtensionArtifactStub, ExtensionArtifactReference, ExtMavenArtifactRepository> =
                this@ExtMavenArtifactRepository.factory

            override val repositoryResolver: RepositoryStubResolver<SimpleMavenRepositoryStub, SimpleMavenRepositorySettings> =
                extRepositoryStubResolver(settings)

            override fun resolve(stub: ExtensionArtifactStub): Job<ExtensionArtifactReference> = job {
                val localSettings = stub.candidates
                    .map(repositoryResolver::resolve)
                    .map { it.merge() }

                val repositories = localSettings.map(factory::createNew)

                val bind = repositories
                    .map { it.get(stub.request)() }
                    .firstOrNull { it.isSuccess }?.merge()

                bind ?: throw ArtifactException.ArtifactNotFound(stub.request.descriptor, repositories.map { it.name })
            }
        }

    override fun get(request: ExtensionArtifactRequest): Job<ExtensionArtifactReference> = job {
        val metadata = handler.requestMetadata(request.descriptor)().merge()

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