//package dev.extframework.extloader.extension
//
//import com.durganmcbroom.artifact.resolver.ArtifactMetadata
//import com.durganmcbroom.artifact.resolver.ArtifactRequest
//import com.durganmcbroom.artifact.resolver.IterableException
//import com.durganmcbroom.artifact.resolver.RepositorySettings
//import com.durganmcbroom.jobs.Job
//import com.durganmcbroom.jobs.job
//import com.durganmcbroom.jobs.mapException
//import dev.extframework.boot.archive.ArchiveException
//import dev.extframework.boot.archive.ArchiveGraph
//import dev.extframework.boot.dependency.DependencyNode
//import dev.extframework.boot.dependency.DependencyResolver
//import dev.extframework.boot.dependency.DependencyResolverProvider
//import dev.extframework.boot.dependency.DependencyTypeContainer
//import dev.extframework.common.util.filterDuplicates
//import dev.extframework.extloader.api.extension.PartitionRuntimeModel
//import dev.extframework.extloader.extension.partition.PartitionLoadException
//
//internal fun dependenciesFromPartition(
//    partition: PartitionRuntimeModel,
//    extName: String,
//    dependencyProviders: DependencyTypeContainer,
//    archiveGraph: ArchiveGraph
//): Job<List<DependencyNode<*>>> = job {
//    partition.dependencies.map { dependency ->
//
//        if (partition.repositories.isEmpty()) {
//            throw PartitionLoadException(
//                partition.name, "Partition: '${partition.name}' has no defined repositories but has dependencies!"
//            ) {
//                extName asContext "Extension name"
//
//                solution("Define at least 1 repository in this partition.")
//            }
//        }
//
//        val requests = partition.repositories.mapNotNull { settings ->
//            val provider: DependencyResolverProvider<*, *, *> =
//                dependencyProviders.get(settings.type) ?: throw PartitionLoadException(
//                    partition.name, "Failed to find dependency type: '${settings.type}'"
//                ) {
//                    partition.name asContext "Partition name"
//                }
//
//            val depReq: ArtifactRequest<*> = provider.parseRequest(dependency) ?: return@mapNotNull null
//
//            val repoSettings = provider.parseSettings(settings.settings) ?: throw PartitionLoadException(
//                partition.name, "Invalid repository settings."
//            ) {
//                extName asContext "Extension name"
//                settings.settings asContext "Repository settings"
//                provider.name asContext "Dependency resolution provider"
//            }
//
//            Triple(depReq, repoSettings, provider)
//        }
//
//        if (requests.isEmpty()) {
//            throw PartitionLoadException(
//                partition.name,
//                "Failed to parse dependency request.",
//            ) {
//                extName asContext "Extension name"
//                dependency asContext "Raw dependency request"
//                partition.repositories asContext "Attempted repositories"
//            }
//        }
//
////        if (requests.any {
////                isInHierarchy(it.first.descriptor)
////                // Decide if returning the ext-loader is really proper (will work though)
////            }) return@map archiveGraph.extLoader!!
//
//        val cacheResult = requests.associateWith findRepo@{ (request, settings, provider) ->
//            val resolver =
//                provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>
//
//            val cacheResult = archiveGraph.cache(
//                request as ArtifactRequest<ArtifactMetadata.Descriptor>, settings, resolver
//            )()
//
//            cacheResult.map {
//                request.descriptor to resolver
//            }
//        }
//
//        if (cacheResult.all { it.value.exceptionOrNull() is ArchiveException.ArchiveNotFound })
//            throw PartitionLoadException(
//                partition.name,
//                "a dependency couldnt be located."
//            ) {
//                extName asContext "Extension name"
//                dependency asContext "Raw dependency request" // We want the raw dependency request because there was an issue with every single dependency provider (and we correctly assume that all may have different dependency descriptor types)
//                requests.map { it.second } asContext "Attempted repositories"
//
//                solution("Make sure all repositories are defined correctly and contain the requested artifact.")
//            }
//
//        val successfulProvider = cacheResult.entries.find { (_, cacheResult) ->
//            cacheResult.isSuccess
//        } ?: throw PartitionLoadException(
//            partition.name,
//            "An unrecoverable error occurred when caching dependencies",
//            IterableException(
//                "Dependencies could not be cached",
//                cacheResult.map { it.value.exceptionOrNull()!! }
//            )
//        ) {
//            dependency asContext "Raw dependency request" // We want the raw dependency request because there was an issue with every single dependency provider (and we correctly assume that all may have different dependency descriptor types)
//            requests.map { it.second } asContext "Attempted repositories"
//        }
//
//        val (descriptor, resolver) = successfulProvider.value.merge()
//
//        archiveGraph.get(
//            descriptor,
//            resolver
//        )().mapException {
//            PartitionLoadException(
//                partition.name,
//                "a dependency failed to load.",
//                it
//            ) {
//                resolver.name asContext "Dependency resolver"
//                descriptor asContext "Dependency descriptor"
//            }
//        }.merge()
//    }.filterDuplicates()
//}
//
