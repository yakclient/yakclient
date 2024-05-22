package net.yakclient.components.extloader.extension

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.IterableException
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.dependency.DependencyResolverProvider
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.common.util.filterDuplicates
import net.yakclient.components.extloader.EXT_LOADER_ARTIFACT
import net.yakclient.components.extloader.EXT_LOADER_GROUP
import net.yakclient.components.extloader.EXT_LOADER_VERSION
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.extension.partition.PartitionLoadException

internal val ArchiveGraph.extLoader: DependencyNode<*>?
    get() = get(
        SimpleMavenDescriptor(
            EXT_LOADER_GROUP, EXT_LOADER_ARTIFACT, EXT_LOADER_VERSION, null
        )
    ) as? DependencyNode<*>


internal fun ArchiveGraph.isInHierarchy(
    descriptor: ArtifactMetadata.Descriptor
): Boolean {
    fun SimpleMavenDescriptor.sameArtifact(other: ArtifactMetadata.Descriptor): Boolean {
        if (other !is SimpleMavenDescriptor) return false
        return group == other.group && artifact == other.artifact
    }

    if (descriptor !is SimpleMavenDescriptor) return false

    val thisNode = extLoader ?: return false

    return descriptor.sameArtifact(thisNode.descriptor) || thisNode.access.targets.any { descriptor.sameArtifact(it.descriptor) }
}

internal fun dependenciesFromPartition(
    partition: ExtensionPartition,
    extName: String,
    dependencyProviders: DependencyTypeContainer,
    archiveGraph: ArchiveGraph
): Job<List<DependencyNode<*>>> = job {
    partition.dependencies.map { dependency ->

        if (partition.repositories.isEmpty()) {
            throw PartitionLoadException(
                partition.name, "Partition: '${partition.name}' has no defined repositories but has dependencies!"
            ) {
                extName asContext "Extension name"

                solution("Define at least 1 repository in this partition.")
            }
        }

        val requests = partition.repositories.mapNotNull { settings ->
            val provider: DependencyResolverProvider<*, *, *> =
                dependencyProviders.get(settings.type) ?: throw PartitionLoadException(
                    partition.name, "Failed to find dependency type: '${settings.type}'"
                ) {
                    partition.name asContext "Partition name"
                }

            val depReq: ArtifactRequest<*> = provider.parseRequest(dependency) ?: return@mapNotNull null

            val repoSettings = provider.parseSettings(settings.settings) ?: throw PartitionLoadException(
                partition.name, "Invalid repository settings."
            ) {
                extName asContext "Extension name"
                settings.settings asContext "Repository settings"
                provider.name asContext "Dependency resolution provider"
            }

            Triple(depReq, repoSettings, provider)
        }

        if (requests.isEmpty()) {
            throw PartitionLoadException(
                partition.name,
                "Failed to parse dependency request.",
            ) {
                extName asContext "Extension name"
                dependency asContext "Raw dependency request"
                partition.repositories asContext "Attempted repositories"
            }
        }

        if (requests.any {
                archiveGraph.isInHierarchy(it.first.descriptor)
                // Decide if returning the ext-loader is really proper (will work though)
            }) return@map archiveGraph.extLoader!!

        val cacheResult = requests.associateWith findRepo@{ (request, settings, provider) ->
            val resolver =
                provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>

            val cacheResult = archiveGraph.cache(
                request as ArtifactRequest<ArtifactMetadata.Descriptor>, settings, resolver
            )()

            cacheResult.map {
                request.descriptor to resolver
            }
        }

        if (cacheResult.all { it.value.exceptionOrNull() is ArchiveException.ArchiveNotFound })
            throw PartitionLoadException(
                partition.name,
                "a dependency couldnt be located."
            ) {
                extName asContext "Extension name"
                dependency asContext "Raw dependency request" // We want the raw dependency request because there was an issue with every single dependency provider (and we correctly assume that all may have different dependency descriptor types)
                requests.map { it.second } asContext "Attempted repositories"

                solution("Make sure all repositories are defined correctly and contain the requested artifact.")
            }

        val successfulProvider = cacheResult.entries.find { (_, cacheResult) ->
            cacheResult.isSuccess
        } ?: throw PartitionLoadException(
            partition.name,
            "An unrecoverable error occurred when caching dependencies",
            IterableException(
                "Dependencies could not be cached",
                cacheResult.map { it.value.exceptionOrNull()!! }
            )
        ) {
            dependency asContext "Raw dependency request" // We want the raw dependency request because there was an issue with every single dependency provider (and we correctly assume that all may have different dependency descriptor types)
            requests.map { it.second } asContext "Attempted repositories"
        }

        val (descriptor, resolver) = successfulProvider.value.merge()

        archiveGraph.get(
            descriptor,
            resolver
        )().mapException {
            PartitionLoadException(
                partition.name,
                "a dependency failed to load.",
                it
            ) {
                resolver.name asContext "Dependency resolver"
                descriptor asContext "Dependency descriptor"
            }
        }.merge()
    }.filterDuplicates()
}

