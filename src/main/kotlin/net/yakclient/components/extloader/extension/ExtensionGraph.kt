package net.yakclient.components.extloader.extension

import asOutput
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.job
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.Archives
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.handleOrChildren
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerLoader
import net.yakclient.boot.container.volume.RootVolume
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.extension.artifact.*
import net.yakclient.components.extloader.extension.versioning.VersionedExtErmArchiveReference
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveHandle
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveReference
import net.yakclient.components.extloader.tweaker.archive.TweakerClassLoader
import net.yakclient.components.extloader.api.environment.EnvironmentAttribute
import net.yakclient.components.extloader.api.environment.EnvironmentAttributeKey
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.environment.dependencyTypesAttrKey
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

@Suppress("NAME_SHADOWING")
public open class ExtensionGraph(
    private val path: Path,
    private val finder: ArchiveFinder<*>,
    private val privilegeManager: PrivilegeManager,
    private val parent: ClassLoader,
    environment: ExtLoaderEnvironment
) : ArchiveGraph<ExtensionDescriptor, ExtensionNode, ExtensionRepositorySettings>(
    ExtensionRepositoryFactory(environment[dependencyTypesAttrKey]!!)
), EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ExtensionGraph
    override val graph: Map<ExtensionDescriptor, ExtensionNode>
        get() = mutableGraph.toMap()

    private val store: CachingDataStore<ExtensionDescriptor, ExtensionRuntimeModel> =
        CachingDataStore(ExtensionDataAccess(path))
    private val applicationReference = environment[ApplicationTarget]!!.reference
    private val dependencyProviders = environment[dependencyTypesAttrKey]!!
    private val extProcessLoader = ExtensionProcessLoader(
        privilegeManager,
        parent,
        environment
    )
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val mutableGraph: MutableMap<ExtensionDescriptor, ExtensionNode> = HashMap()

    public companion object : EnvironmentAttributeKey<ExtensionGraph>

    override fun isCached(descriptor: ExtensionDescriptor): Boolean {
        return store.contains(descriptor)
    }

    override fun cacherOf(settings: ExtensionRepositorySettings): ExtensionCacher {
        return ExtensionCacher(ExtensionRepositoryFactory(dependencyProviders).createContext(settings))
    }

    private fun getBasePathFor(desc: ExtensionDescriptor): Path = path resolve desc.group.replace(
        '.', File.separatorChar
    ) resolve desc.artifact resolve desc.version

    private fun getJarPathFor(desc: ExtensionDescriptor): Path =
        getBasePathFor(desc) resolve "${desc.artifact}-${desc.version}.jar"

    private fun getErmPathFor(desc: ExtensionDescriptor): Path =
        getBasePathFor(desc) resolve "${desc.artifact}-${desc.version}-erm.json"

    override suspend fun get(
        descriptor: ExtensionDescriptor
    ): JobResult<ExtensionNode, ArchiveLoadException> = job(JobName("Load extension: '${descriptor.name}'")) {
        graph[descriptor] ?: run {
            val path = getJarPathFor(descriptor)

            val ermPath = getErmPathFor(descriptor).takeIf(Files::exists) ?: fail(
                ArchiveLoadException.IllegalState("Extension runtime model for request: '${descriptor}' not found cached.")
            )
            val erm: ExtensionRuntimeModel = mapper.readValue(ermPath.toFile())

            val reference = path.takeIf(Files::exists)?.let(finder::find)?.let {
                VersionedExtErmArchiveReference(
                    it, applicationReference.descriptor.version, erm
                )
            }

            val children = erm.extensions.map {
                val extRequest = dependencyProviders.get("simple-maven")!!.parseRequest(it)
                    ?: fail(ArchiveLoadException.IllegalState("Illegal extension request: '$it'"))
                async {
                    get((extRequest as ExtensionArtifactRequest).descriptor).attempt()
                }
            }

            val allPartitions = reference?.enabledPartitions?.plus(reference.mainPartition) ?: listOf()

            val allDependencies = allPartitions
                .flatMapTo(HashSet(), ExtensionPartition::dependencies)
            val allDependencyRepositories = allPartitions
                .flatMapTo(HashSet(), ExtensionPartition::repositories)

            val dependencies = allDependencies.map { dep ->
                allDependencyRepositories.firstNotNullOfOrNull find@{ repo ->
                    val provider =
                        dependencyProviders.get(repo.type)
                            ?: fail(ArchiveLoadException.DependencyTypeNotFound(repo.type))
                    val depReq = provider.parseRequest(dep) ?: return@find null

                    async {
                        (provider.graph as DependencyGraph<ArtifactMetadata.Descriptor, *>).get(depReq.descriptor)
                            .attempt()
                    }
                }
                    ?: fail(ArchiveLoadException.IllegalState("Couldn't load dependency: '${dep}' for extension: '${descriptor}'"))
            }

            val tweaker = erm.tweakerPartition?.let { partition ->
                async {
                    val dependencies = partition.dependencies.map { dep ->
                        partition.repositories.firstNotNullOfOrNull resolveRepo@{
                            val provider = dependencyProviders.get(it.type)
                                ?: fail(ArchiveLoadException.DependencyTypeNotFound(it.type))
                            val depReq: ArtifactRequest<*> = provider.parseRequest(dep) ?: return@resolveRepo null

                            async {
                                (provider.graph as DependencyGraph<SimpleMavenDescriptor, SimpleMavenRepositorySettings>)
                                    .get(depReq.descriptor as SimpleMavenDescriptor)
                                    .attempt()
                            }
                        }
                            ?: throw IllegalArgumentException("Couldnt load dependency : '${dep}' (unable to parse name) for tweaker: '${erm.name}'")
                    }

                    val ref = TweakerArchiveReference(
                        partition.path.removeSuffix("/") + "/",
                        Archives.find(path, Archives.Finders.ZIP_FINDER)
                    )

                    val archiveDependencies: Set<ArchiveHandle> =
                        dependencies.awaitAll().flatMapTo(HashSet()) { it.handleOrChildren() }

                    val archive = TweakerArchiveHandle(
                        erm.name + "-tweaker",
                        TweakerClassLoader(ref, archiveDependencies, parent),
                        ref,
                        archiveDependencies.toSet()
                    )

                    val entrypoint = archive.classloader.loadClass(partition.entrypoint)

                    val tweaker = (entrypoint.getConstructor().newInstance() as? EnvironmentTweaker) ?: fail(
                        ArchiveLoadException.IllegalState("Given extension: '${erm.name}' has a tweaker that does not implement: '${EnvironmentTweaker::class.qualifiedName}'")
                    )

                    tweaker to archive
                }
            }


//            val environmentTweakers = erm.environmentTweakers.map { dep ->
//                erm.environmentTweakerRepositories.firstNotNullOfOrNull {
//                    check(it.type == "simple-maven") {"Unknown repository type: '${it.type}' in erm: '${erm.name}'. Environment tweakers can only be maven based."}
//
//                    val provider = dependencyProviders.get("simple-maven") ?: fail(ArchiveLoadException.DependencyTypeNotFound("simple-maven"))
//                    val depReq: ArtifactRequest<*> = provider.parseRequest(dep) ?: return@firstNotNullOfOrNull null
//
//                    async {
//                        (environment[EnvironmentTweakerGraph]!!).get(depReq.descriptor as SimpleMavenDescriptor).attempt()
//                    }
//                } ?: fail(ArchiveLoadException.IllegalState("Couldn't load dependency: '${dep}' for extension: '${descriptor}'"))
//            }

            val containerHandle = ContainerLoader.createHandle<ExtensionProcess>()

            fun containerOrChildren(node: ExtensionNode): List<Container<ExtensionProcess>> =
                node.extension?.let(::listOf) ?: node.children.flatMap { containerOrChildren(it) }

            val awaitedChildren = children.awaitAll()
            val awaitedDependencies = dependencies.awaitAll()
            val (awaitedTweaker, awaitedTweakerHandle) = tweaker?.await() ?: (null to null)
//            val awaitedTweakers = environmentTweakers.awaitAll()


            val container = if (reference != null) {
                ContainerLoader.load(
                    ExtensionInfo(
                        reference,
                        awaitedChildren.flatMap(::containerOrChildren),
                        awaitedDependencies.flatMap { it.handleOrChildren() } + listOfNotNull(awaitedTweakerHandle),
                        erm,
                        containerHandle
                    ),
                    containerHandle,
                    extProcessLoader,
                    RootVolume.derive(erm.name, getBasePathFor(descriptor)),
                    privilegeManager
                )
            } else null

            ExtensionNode(
                descriptor,
                reference,
                awaitedChildren.toSet(),
                awaitedDependencies.toSet(),
                container,
                erm,
                awaitedTweaker
            ).also {
                mutableGraph[descriptor] = it
            }
        }
    }

    public open inner class ExtensionCacher(
        resolver: ResolutionContext<ExtensionArtifactRequest, ExtensionStub, ArtifactReference<*, ExtensionStub>>,
    ) : ArchiveCacher<ExtensionArtifactRequest, ExtensionStub>(
        resolver
    ) {
        private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        override suspend fun cache(request: ExtensionArtifactRequest): JobResult<Unit, ArchiveLoadException> {
            val desc = request.descriptor
            val path = getJarPathFor(desc)
            if (!path.exists()) {
                val ref = resolver.repositoryContext.artifactRepository.get(request)
                    .mapLeft(ArchiveLoadException::ArtifactLoadException)
                    .asOutput()

                if (ref.wasFailure()) return ref.map {}

                val cache =
                    cache(request, ref.orNull() as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>)
                if (cache.wasFailure()) {
                    return JobResult.Failure((cache as JobResult.Failure).output)
                }
            }

            return JobResult.Success(Unit)
        }

        private suspend fun cache(
            request: ExtensionArtifactRequest,
            ref: ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>,
        ): JobResult<Unit, ArchiveLoadException> = job(JobName("Cache extension: '${request.descriptor.name}'")) {
            ref.children.map { stub ->
                val resolve = resolver.resolverContext.stubResolver.resolve(stub)

                val childRef = resolve.mapLeft(ArchiveLoadException::ArtifactLoadException)
                    .asOutput()
                    .attempt() as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>

                async {
                    cache(stub.request, childRef).attempt()
                }
            }.awaitAll()

            val metadata: ExtensionArtifactMetadata = ref.metadata

            val allPartitions = metadata.erm.versionPartitions + listOfNotNull(metadata.erm.tweakerPartition)

            val allDependencies = allPartitions
                .flatMapTo(HashSet(), ExtensionPartition::dependencies)
            val allDependencyRepositories = allPartitions
                .flatMapTo(HashSet(), ExtensionPartition::repositories)

            allDependencies
                .forEach { dependency ->
                    allDependencyRepositories.firstNotNullOfOrNull findRepo@{ settings ->
                        val provider = dependencyProviders.get(settings.type) ?: return@findRepo null

                        val depReq = provider.parseRequest(dependency) ?: return@findRepo null

                        val repoSettings = provider.parseSettings(settings.settings) ?: return@findRepo null

                        val loader =
                            (provider.graph as DependencyGraph<*, RepositorySettings>).cacherOf(
                                repoSettings
                            )

                        (loader as DependencyGraph<*, RepositorySettings>.DependencyCacher<ArtifactRequest<*>, *>).cache(
                            depReq
                        ).orNull()
                    }
                        ?: fail(ArchiveLoadException.IllegalState("Failed to load dependency: '$dependency' from repositories '${allDependencyRepositories}''"))
                }

            val ermPath = getErmPathFor(request.descriptor)
            if (!Files.exists(ermPath)) ermPath.make()

            ermPath.writeBytes(mapper.writeValueAsBytes(metadata.erm))

            val resource = metadata.resource
            if (resource != null) {
                val jarPath = getJarPathFor(request.descriptor)
                if (!Files.exists(jarPath)) jarPath.make()


                Channels.newChannel(resource.open()).use { cin ->
                    FileOutputStream(jarPath.toFile()).use { fout ->
                        fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                    }
                }
            }

            store.put(request.descriptor, metadata.erm)
        }
    }
}
