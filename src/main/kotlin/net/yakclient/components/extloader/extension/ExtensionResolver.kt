package net.yakclient.components.extloader.extension

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositoryStub
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.jobScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archives.ArchiveFinder
import net.yakclient.boot.archive.*
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerLoader
import net.yakclient.boot.container.volume.RootVolume
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.dependency.DependencyResolverProvider
import net.yakclient.boot.maven.MavenLikeResolver
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.util.*
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.ProvidedResource
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.extension.artifact.*
import net.yakclient.components.extloader.extension.versioning.VersionedExtErmArchiveReference
import net.yakclient.components.extloader.tweaker.EnvironmentTweakerResolver
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

public open class ExtensionResolver(
    private val path: Path,
    private val finder: ArchiveFinder<*>,
    private val privilegeManager: PrivilegeManager,
    parent: ClassLoader,
    environment: ExtLoaderEnvironment,
) : MavenLikeResolver<ExtensionArtifactRequest, ExtensionNode, ExtensionRepositorySettings, SimpleMavenRepositoryStub, ExtensionArtifactMetadata>,
    EnvironmentAttribute {

    override val key: EnvironmentAttributeKey<*> = ExtensionResolver
    override val factory: RepositoryFactory<ExtensionRepositorySettings, ExtensionArtifactRequest, *, ArtifactReference<ExtensionArtifactMetadata, *>, *> =
        ExtensionRepositoryFactory(environment[dependencyTypesAttrKey]!!.container)
    override val metadataType: KClass<ExtensionArtifactMetadata> = ExtensionArtifactMetadata::class
    override val nodeType: KClass<ExtensionNode> = ExtensionNode::class
    override val name: String = "extension"

    private val applicationReference = environment[ApplicationTarget]!!.reference
    private val dependencyProviders = environment[dependencyTypesAttrKey]!!.container
    private val extProcessLoader = ExtensionProcessLoader(privilegeManager, parent, environment)
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val archiveGraph = environment.archiveGraph

    public companion object : EnvironmentAttributeKey<ExtensionResolver>

    private fun getBasePathFor(desc: ExtensionDescriptor): Path = path resolve desc.group.replace(
        '.', File.separatorChar
    ) resolve desc.artifact resolve desc.version

    override suspend fun load(
        data: ArchiveData<ExtensionDescriptor, CachedArchiveResource>,
        resolver: ChildResolver
    ): JobResult<ExtensionNode, ArchiveException> = jobScope {
        val erm = data.resources.requireKeyInDescriptor("erm.json").path.let { mapper.readValue<ExtensionRuntimeModel>(it.toFile()) }

        val reference = data.resources["jar.jar"]
            ?.path
            ?.takeIf(Files::exists)
            ?.let(finder::find)
            ?.let {
                VersionedExtErmArchiveReference(
                    it, applicationReference.descriptor.version, erm
                )
            }

        val (extensions, dependencies) = data.children.map {
            val localResolver =
                if (it.resolver == name) this@ExtensionResolver else dependencyProviders.get(it.resolver)?.resolver
                    ?: fail(ArchiveException.ArchiveTypeNotFound(it.resolver, trace()))

            async {
                resolver.load(
                    it.descriptor,
                    localResolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *, *>
                )
            }
        }.awaitAll().partition { it is ExtensionNode } as Pair<List<ExtensionNode>, List<DependencyNode>>

        val containerHandle = ContainerLoader.createHandle<ExtensionProcess>()

        fun containerOrChildren(node: ExtensionNode): List<Container<ExtensionProcess>> =
            node.extension?.let(::listOf) ?: node.children.flatMap { containerOrChildren(it) }

        val tweakerDescriptor = data.descriptor.copy(
            classifier = "tweaker"
        )

        val tweakerNode =
            archiveGraph[tweakerDescriptor]
//            environment[EnvironmentTweakerResolver]?.let {
//                archiveGraph.get(
//                    tweakerDescriptor, it
//                )
//            }?.attempt()

        val container = if (reference != null) {
            ContainerLoader.load(
                ExtensionInfo(
                    reference,
                    extensions.flatMap(::containerOrChildren),
                    dependencies.flatMap { it.handleOrChildren() } + listOfNotNull(tweakerNode?.archive),
                    erm,
                    containerHandle
                ),
                containerHandle,
                extProcessLoader,
                RootVolume.derive(erm.name, getBasePathFor(data.descriptor)),
                privilegeManager
            )
        } else null

        ExtensionNode(
            data.descriptor,
            reference,
            extensions.toSet(),
            dependencies.toSet(),
            container,
            erm,
        )
    }

    override suspend fun cache(
        ref: ArtifactReference<ExtensionArtifactMetadata, ArtifactStub<ExtensionArtifactRequest, SimpleMavenRepositoryStub>>,
        helper: ArchiveCacheHelper<SimpleMavenRepositoryStub, ExtensionRepositorySettings>
    ): JobResult<ArchiveData<ExtensionDescriptor, CacheableArchiveResource>, ArchiveException> = jobScope {
        val children = ref.children.map { stub ->
            async {
                stub.candidates.firstNotFailureOf {
                    helper.cache(
                        stub.request,
                        helper.resolve(it).attempt(),
                        this@ExtensionResolver
                    )
                }.attempt()
            }
        }.awaitAll()

        val metadata: ExtensionArtifactMetadata = ref.metadata
        val erm = metadata.erm

        val allPartitions = erm.versionPartitions + listOfNotNull(erm.mainPartition, erm.tweakerPartition)

        val dependencies =
            allPartitions
                .flatMap { p -> p.dependencies.map { it to p } }
                .map { (dependency, p) ->
                    val trace = trace()

                    p.repositories.firstNotFailureOf  findRepo@{ settings ->
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

                        helper.cache(
                            depReq as ArtifactRequest<ArtifactMetadata.Descriptor>,
                            repoSettings,
                            provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, RepositorySettings, RepositoryStub, *>
                        )
                    }.mapFailure {
                        ArchiveException.IllegalState("Failed to load dependency: '$dependency' from repositories '${p.repositories}'. Error was: '$it'.",
                            trace
                        )
                    }.attempt()
                }

        ArchiveData(
            ref.metadata.descriptor,
            mapOfNonNullValues(
                "jar.jar" to ref.metadata.resource?.toSafeResource()?.let(::CacheableArchiveResource),
                "erm.json" to CacheableArchiveResource(
                    ProvidedResource(URI.create("http://nothing")) { mapper.writeValueAsBytes(ref.metadata.erm) }
                )
            ),
            children + dependencies
        )
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
