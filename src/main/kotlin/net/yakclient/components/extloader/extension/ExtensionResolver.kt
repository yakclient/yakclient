package net.yakclient.components.extloader.extension

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.jobScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.*
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.dependency.DependencyResolverProvider
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveResourceProvider
import net.yakclient.boot.loader.ClassProvider
import net.yakclient.boot.loader.ResourceProvider
import net.yakclient.boot.maven.MavenLikeResolver
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.util.*
import net.yakclient.common.util.resource.ProvidedResource
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.extension.artifact.*
import net.yakclient.components.extloader.extension.versioning.VersionedExtErmArchiveReference
import net.yakclient.components.extloader.target.TargetLinker
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

public open class ExtensionResolver(
    private val finder: ArchiveFinder<*>,
    privilegeManager: PrivilegeManager,
    parent: ClassLoader,
    private val environment: ExtLoaderEnvironment,
) : MavenLikeResolver<ExtensionArtifactRequest, ExtensionNode, ExtensionRepositorySettings, ExtensionArtifactMetadata>,
    EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ExtensionResolver
    override val factory: RepositoryFactory<ExtensionRepositorySettings, ExtensionArtifactRequest, *, ArtifactReference<ExtensionArtifactMetadata, *>, *> =
        ExtensionRepositoryFactory(environment[dependencyTypesAttrKey]!!.container)
    override val metadataType: Class<ExtensionArtifactMetadata> = ExtensionArtifactMetadata::class.java
    override val nodeType: Class<ExtensionNode> = ExtensionNode::class.java
    override val name: String = "extension"

    private val applicationReference = environment[ApplicationTarget]!!.reference
    private val dependencyProviders = environment[dependencyTypesAttrKey]!!.container
    private val referenceLoader = ExtensionContainerLoader(privilegeManager, parent, environment)
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val archiveGraph = environment.archiveGraph
    private val target by lazy {environment[TargetLinker]!! }

    public companion object : EnvironmentAttributeKey<ExtensionResolver>

    private data class ExtraDependencyInfo(
        val descriptor: Map<String, String>,
        val resolver: String,
        val partition: String,
    )

    override suspend fun load(
        data: ArchiveData<ExtensionDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): JobResult<ExtensionNode, ArchiveException> = jobScope {
        val erm =
            data.resources.requireKeyInDescriptor("erm.json").path.let { mapper.readValue<ExtensionRuntimeModel>(it.toFile()) }

        val reference = data.resources["jar.jar"]
            ?.path
            ?.takeIf(Files::exists)
            ?.let(finder::find)
            ?.let {
                VersionedExtErmArchiveReference(
                    it, applicationReference.descriptor.version, erm
                )
            }

        val extraDependencyInfo =
            mapper.readValue<List<ExtraDependencyInfo>>(data.resources["extra-dep-info.json"]!!.path.toFile())

        val dependencies = extraDependencyInfo
            .filter { (_, _, partitionName) ->
                if (partitionName == "main" || partitionName == "tweaker") true
                else {
                    erm.versionPartitions.find {
                        it.name == partitionName
                    }?.supportedVersions?.contains(environment[ApplicationTarget]!!.reference.descriptor.version)
                        ?: false
                }
            }.map { (dependency, resolver) ->
                val dependencyResolver = helper.getResolver(
                    resolver,
                    ArtifactMetadata.Descriptor::class.java,
                    DependencyNode::class.java as Class<out DependencyNode<*>>
                ).attempt()
                val desc = dependencyResolver.deserializeDescriptor(dependency).attempt()

                async {
                    helper.load(
                        desc,
                        dependencyResolver
                    )
                }
            }.awaitAll()

        val parents = data.parents.map {
            helper.load(
                it.descriptor,
                this@ExtensionResolver
            )
        }

        fun handleOrParents(node: ArchiveNode<*>): List<ArchiveHandle> =
            node.archive?.let(::listOf) ?: node.parents.flatMap { handleOrParents(it) }

        val tweakerDescriptor = data.descriptor.copy(
            classifier = "tweaker"
        )

        val tweakerNode =
            archiveGraph[tweakerDescriptor]

        val access = helper.newAccessTree {
            allDirect(dependencies)
            if (tweakerNode != null) direct(tweakerNode)

            rawTarget(
                target.targetTarget
            )
            parents.forEach {
                rawTarget(ArchiveTarget(
                    it.descriptor,
                    object : ArchiveRelationship {
                        override val name: String = "Lazy Direct"
                        override val classes: ClassProvider by lazy {
                            ArchiveClassProvider(it.archive)
                        }

                        override val resources: ResourceProvider by lazy {
                            ArchiveResourceProvider(it.archive)
                        }
                    }
                ))
            }
        }

        val extRef = if (reference != null) referenceLoader.load(
            reference,
            parents,
            dependencies.mapNotNull { it.archive },
            erm,
            access,
        ).attempt() else null

        ExtensionNode(
            data.descriptor,
            reference,
            parents.toSet(),
            dependencies.toSet(),
            extRef,
            erm,
            access,
            this@ExtensionResolver
        )
    }

    override suspend fun cache(
        metadata: ExtensionArtifactMetadata,
        helper: ArchiveCacheHelper<ExtensionDescriptor>
    ): JobResult<ArchiveData<ExtensionDescriptor, CacheableArchiveResource>, ArchiveException> = jobScope {
        val erm = metadata.erm

        val allDependencies =
            ((erm.versionPartitions + listOfNotNull(erm.mainPartition, erm.tweakerPartition))
                .flatMap { p -> p.dependencies.map { it to p } }
                .map { (dependency, p) ->
                    val trace = trace()

                    var dependencyDescriptor: ArtifactMetadata.Descriptor? = null
                    var dependencyResolver: ArchiveNodeResolver<*, *, *, *, *>? = null

                    p.repositories.firstNotFailureOf findRepo@{ settings ->
                        val provider: DependencyResolverProvider<*, *, *> =
                            dependencyProviders.get(settings.type) ?: fail(
                                ArchiveException.ArchiveTypeNotFound(settings.type, trace)
                            )

                        val depReq: ArtifactRequest<*> = provider.parseRequest(dependency) ?: casuallyFail(
                            ArchiveException.DependencyInfoParseFailed("Failed to parse request: '$dependency'", trace)
                        )

                        val repoSettings = provider.parseSettings(settings.settings) ?: casuallyFail(
                            ArchiveException.DependencyInfoParseFailed("Failed to parse settings: '$settings'", trace)
                        )

                        dependencyDescriptor = depReq.descriptor
                        dependencyResolver = provider.resolver

                        helper.cache(
                            depReq as ArtifactRequest<ArtifactMetadata.Descriptor>,
                            repoSettings,
                            provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>
                        )
                    }.mapFailure {
                        ArchiveException.IllegalState(
                            "Failed to load dependency: '$dependency' from repositories '${p.repositories}'. Error was: '$it'.",
                            trace
                        )
                    }.attempt()

                    Triple(dependencyDescriptor!!, dependencyResolver!!, p)
                })

        helper.withResource("jar.jar", metadata.resource?.toSafeResource())
        helper.withResource(
            "erm.json",
            ProvidedResource(URI.create("http://nothing")) { mapper.writeValueAsBytes(metadata.erm) }
        )
        helper.withResource(
            "extra-dep-info.json",
            ProvidedResource(URI.create("http://nothing")) {
                mapper.writeValueAsBytes(
                    allDependencies.map { (descriptor, resolver, partition) ->
                        val serializedDescriptor =
                            (resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>).serializeDescriptor(
                                descriptor
                            )

                        ExtraDependencyInfo(
                            serializedDescriptor,
                            resolver.name,
                            partition.name
                        )
                    }
                )
            }
        )

        helper.newData(metadata.descriptor)
    }

//    override suspend fun load(
//        descriptor: ExtensionDescriptor
//    ): JobResult<ExtensionNode, ArchiveException> {
//        return job(JobName("Load extension: '${descriptor.name}'")) {
//            val path = getJarPathFor(descriptor)
//
//            val ermPath = getErmPathFor(descriptor).takeIf(Files::exists) ?: fail(
//                ArchiveException.IllegalState("Extension runtime model for request: '${descriptor}' not found cached.")
//            )
//            val erm: ExtensionRuntimeModel = mapper.readValue(ermPath.toFile())
//
//            val reference = path.takeIf(Files::exists)?.let(finder::find)?.let {
//                VersionedExtErmArchiveReference(
//                    it, applicationReference.descriptor.version, erm
//                )
//            }
//
//            val children = erm.extensions.map {
//                val extRequest = dependencyProviders.get("simple-maven")!!.parseRequest(it)
//                    ?: fail(ArchiveException.IllegalState("Illegal extension request: '$it'"))
//                async {
//                    load((extRequest as ExtensionArtifactRequest).descriptor).attempt()
//                }
//            }
//
//            val allPartitions = reference?.enabledPartitions?.plus(reference.mainPartition) ?: listOf()
//
//            val allDependencies = allPartitions
//                .flatMapTo(HashSet(), ExtensionPartition::dependencies)
//            val allDependencyRepositories = allPartitions
//                .flatMapTo(HashSet(), ExtensionPartition::repositories)
//
//            val dependencies = allDependencies.map { dep ->
//                allDependencyRepositories.firstNotNullOfOrNull find@{ repo ->
//                    val provider =
//                        dependencyProviders.get(repo.type)
//                            ?: fail(ArchiveException.DependencyTypeNotFound(repo.type))
//                    val depReq = provider.parseRequest(dep) ?: return@find null
//
//                    async {
//                        archiveGraph.get(
//                            depReq.descriptor,
//                            provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, *, *>
//                        ).attempt()
//                    }
//                }
//                    ?: fail(ArchiveException.IllegalState("Couldn't load dependency: '${dep}' for extension: '${descriptor}'"))
//            }
//
//            val awaitedChildren = children.awaitAll()
//
//            val containerHandle = ContainerLoader.createHandle<ExtensionProcess>()
//
//            fun containerOrChildren(node: ExtensionNode): List<Container<ExtensionProcess>> =
//                node.extension?.let(::listOf) ?: node.children.flatMap { containerOrChildren(it) }
//
//            val awaitedDependencies = dependencies.awaitAll()
//
//            val tweakerNode =
//                environment[EnvironmentTweakerResolver]!!.takeIf {
//                    it.isCached(descriptor)
//                }?.let {
//                    archiveGraph.get(
//                        descriptor, it
//                    )
//                }?.attempt()
//
//            val container = if (reference != null) {
//                ContainerLoader.load(
//                    ExtensionInfo(
//                        reference,
//                        awaitedChildren.flatMap(::containerOrChildren),
//                        awaitedDependencies.flatMap { it.handleOrChildren() } + listOfNotNull(tweakerNode?.archive),
//                        erm,
//                        containerHandle
//                    ),
//                    containerHandle,
//                    extProcessLoader,
//                    RootVolume.derive(erm.name, getBasePathFor(descriptor)),
//                    privilegeManager
//                )
//            } else null
//
//            ExtensionNode(
//                descriptor,
//                reference,
//                awaitedChildren.toSet(),
//                awaitedDependencies.toSet(),
//                container,
//                erm,
//            )
//            //            .also {
//            //            mutableGraph[descriptor] = it
//            //        }
//        }
//    }
//
////    public open inner class ExtensionCacher(
////        resolver: ResolutionContext<ExtensionArtifactRequest, ExtensionStub, ArtifactReference<*, ExtensionStub>>,
////    ) : ArchiveCacher<ExtensionArtifactRequest, ExtensionStub>(
////        resolver
////    ) {
////        private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
//
//    override suspend fun cache(
//        request: ExtensionArtifactRequest,
//        repository: ExtensionRepositorySettings,
//    ): JobResult<Unit, ArchiveException> {
//        val resolver = factory.createContext(repository)
//
//        val desc = request.descriptor
//        val path = getJarPathFor(desc)
//        if (!path.exists()) {
//            val ref = resolver.repositoryContext.artifactRepository.get(request)
//                .mapLeft(ArchiveException::ArtifactLoadException)
//                .asOutput()
//
//            if (ref.wasFailure()) return ref.map {}
//
//            val cache =
//                cache(
//                    request,
//                    resolver,
//                    ref.orNull() as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>
//                )
//            if (cache.wasFailure()) {
//                return JobResult.Failure((cache as JobResult.Failure).output)
//            }
//        }
//
//        return JobResult.Success(Unit)
//    }
//
//    private suspend fun cache(
//        request: ExtensionArtifactRequest,
//        resolver: ResolutionContext<ExtensionArtifactRequest, ExtensionArtifactStub, ExtensionArtifactReference>,
//        ref: ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>,
//    ): JobResult<Unit, ArchiveException> = job(JobName("Cache extension: '${request.descriptor.name}'")) {
//        ref.children.map { stub ->
//            val resolve = resolver.resolverContext.stubResolver.resolve(stub)
//
//            val childRef = resolve.mapLeft(ArchiveException::ArtifactLoadException)
//                .asOutput()
//                .attempt() as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>
//
//            async {
//                cache(stub.request, resolver, childRef).attempt()
//            }
//        }.awaitAll()
//
//        val metadata: ExtensionArtifactMetadata = ref.metadata
//        val erm = metadata.erm
//
//        val allPartitions = erm.versionPartitions + listOfNotNull(erm.mainPartition, erm.tweakerPartition)
//
//        val allDependencies = allPartitions
//            .flatMapTo(HashSet(), ExtensionPartition::dependencies)
//        val allDependencyRepositories = allPartitions
//            .flatMapTo(HashSet(), ExtensionPartition::repositories)
//
//        allDependencies
//            .forEach { dependency ->
//                allDependencyRepositories.firstNotNullOfOrNull findRepo@{ settings ->
//                    val provider: DependencyResolverProvider<*, *, *> =
//                        dependencyProviders.get(settings.type) ?: return@findRepo null
//
//                    val depReq = provider.parseRequest(dependency) ?: return@findRepo null
//
//                    val repoSettings = provider.parseSettings(settings.settings) ?: return@findRepo null
//
//                    archiveGraph.cache(
//                        depReq,
//                        repoSettings,
//                        provider.resolver as DependencyResolver<*, ArtifactRequest<*>, RepositorySettings>
//                    ).attempt()
//
////                    val loader =
////                        (provider.resolver as DependencyResolver<*, *, RepositorySettings>).cacherOf(
////                            repoSettings
////                        )
////
////                    (loader as DependencyResolver<*, *, RepositorySettings>.DependencyCacher<ArtifactRequest<*>, *>).cache(
////                        depReq
////                    ).orNull()
//                }
//                    ?: fail(ArchiveException.IllegalState("Failed to load dependency: '$dependency' from repositories '${allDependencyRepositories}''"))
//            }
//
//        val ermPath = getErmPathFor(request.descriptor)
//        if (!Files.exists(ermPath)) ermPath.make()
//
//        ermPath.writeBytes(mapper.writeValueAsBytes(erm))
//
//        val resource = metadata.resource
//        if (resource != null) {
//            val jarPath = getJarPathFor(request.descriptor)
//            if (!Files.exists(jarPath)) jarPath.make()
//
//
//            Channels.newChannel(resource.open()).use { cin ->
//                FileOutputStream(jarPath.toFile()).use { fout ->
//                    fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
//                }
//            }
//        }
//
//        store.put(request.descriptor, erm)
//    }
}
