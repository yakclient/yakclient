package net.yakclient.components.extloader.extension

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import arrow.core.right
import com.durganmcbroom.artifact.resolver.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.handleOrChildren
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerLoader
import net.yakclient.boot.container.volume.RootVolume
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.DependencyTypeProvider
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.extension.versioning.VersionedExtErmArchiveReference
import net.yakclient.components.extloader.extension.artifact.*
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

public class ExtensionGraph(
    private val path: Path,
    private val finder: ArchiveFinder<*>,
    private val privilegeManager: PrivilegeManager,
    parent: ClassLoader,
    private val dependencyProviders: DependencyTypeProvider,
    mappings: ArchiveMapping,
    minecraftRef: ArchiveReference,
    private val minecraftVersion: String,
) : ArchiveGraph<ExtensionDescriptor, ExtensionNode, ExtensionRepositorySettings>(
    ExtensionRepositoryFactory(dependencyProviders)
) {
    private val store: CachingDataStore<ExtensionDescriptor, ExtensionRuntimeModel> = CachingDataStore(ExtensionDataAccess(path))
    private val extProcessLoader = ExtensionProcessLoader(
        privilegeManager, parent,  mappings, minecraftRef
    )
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val mutableGraph: MutableMap<ExtensionDescriptor, ExtensionNode> = HashMap()
    override val graph: Map<ExtensionDescriptor, ExtensionNode>
        get() = mutableGraph.toMap()

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

    override fun get(descriptor: ExtensionDescriptor): Either<ArchiveLoadException, ExtensionNode> {
        return graph[descriptor]?.right() ?: either.eager {
            val path = getJarPathFor(descriptor)

            val ermPath = ensureNotNull(getErmPathFor(descriptor).takeIf(Files::exists)) {
                ArchiveLoadException.IllegalState("Extension runtime model for request: '${descriptor}' not found cached.")
            }
            val erm: ExtensionRuntimeModel = mapper.readValue(ermPath.toFile())

            val reference = path.takeIf(Files::exists)?.let(finder::find)?.let {
                VersionedExtErmArchiveReference(
                    it, minecraftVersion, erm
                )
            }

            val children: List<ExtensionNode> = erm.extensions.map {
                val extRequest = dependencyProviders["simple-maven"]!!.parseRequest(it)
                    ?: shift(ArchiveLoadException.IllegalState("Illegal extension request: '$it'"))
                get((extRequest as ExtensionArtifactRequest).descriptor).bind()
            }

            val allPartitions = erm.versionPartitions

            val allDependencies = allPartitions
                .flatMapTo(HashSet(), ExtensionVersionPartition::dependencies)
            val allDependencyRepositories = allPartitions
                .flatMapTo(HashSet(), ExtensionVersionPartition::repositories)

            val dependencies = allDependencies.map { dep ->
                val find = allDependencyRepositories.firstNotNullOfOrNull find@{ repo ->
                    val provider =
                        dependencyProviders[repo.type] ?: shift(ArchiveLoadException.DependencyTypeNotFound(repo.type))
                    val depReq = provider.parseRequest(dep) ?: return@find null

                    (provider.graph as DependencyGraph<ArtifactMetadata.Descriptor, *>).get(depReq.descriptor).orNull()
                }
                find
                    ?: shift(ArchiveLoadException.IllegalState("Couldn't load dependency: '${dep}' for extension: '${descriptor}'"))
            }


            val containerHandle = ContainerLoader.createHandle<ExtensionProcess>()

            fun containerOrChildren(node: ExtensionNode): List<Container<ExtensionProcess>> =
                node.extension?.let(::listOf) ?: children.flatMap { containerOrChildren(it) }

            val container = if (reference != null) ContainerLoader.load(
                ExtensionInfo(
                    reference,
                    children.flatMap(::containerOrChildren),
                    dependencies.flatMap { it.handleOrChildren() },
                    erm,
                    containerHandle
                ),
                containerHandle,
                extProcessLoader,
                RootVolume.derive(erm.name, getBasePathFor(descriptor)),
                privilegeManager
            ) else null

            ExtensionNode(
                reference, children.toSet(), dependencies.toSet(), container, erm
            ).also {
                mutableGraph[descriptor] = it
            }
        }
    }

    public inner class ExtensionCacher(
        resolver: ResolutionContext<ExtensionArtifactRequest, ExtensionStub, ArtifactReference<*, ExtensionStub>>,
    ) : ArchiveCacher<ExtensionArtifactRequest, ExtensionStub>(
        resolver
    ) {
        private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        override fun cache(request: ExtensionArtifactRequest): Either<ArchiveLoadException, Unit> = either.eager {
            val desc = request.descriptor
            val path = getJarPathFor(desc)
            if (!path.exists()) {
                val ref = resolver.repositoryContext.artifactRepository.get(request)
                    .mapLeft(ArchiveLoadException::ArtifactLoadException).bind()

                cache(request, ref as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>).bind()
            }
        }

        private fun cache(
            request: ExtensionArtifactRequest,
            ref: ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>,
        ): Either<ArchiveLoadException, Unit> = either.eager {
            ref.children.forEach { stub ->
                val resolve = resolver.resolverContext.stubResolver.resolve(stub)

                val childRef = resolve.mapLeft(ArchiveLoadException::ArtifactLoadException)
                    .bind() as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>

                cache(stub.request, childRef).bind()
            }

            val metadata: ExtensionArtifactMetadata = ref.metadata

            val allPartitions = metadata.erm.versionPartitions

            val allDependencies = allPartitions
                .flatMapTo(HashSet(), ExtensionVersionPartition::dependencies)
            val allDependencyRepositories = allPartitions
                .flatMapTo(HashSet(), ExtensionVersionPartition::repositories)

            allDependencies
                .forEach { dependency ->
                    allDependencyRepositories.firstNotNullOfOrNull findRepo@{ settings ->
                        val provider = dependencyProviders[settings.type] ?: return@findRepo null

                        val depReq = provider.parseRequest(dependency) ?: return@findRepo null

                        val repoSettings = provider.parseSettings(settings.settings) ?: return@findRepo null

                        val loader =
                            (provider.graph as DependencyGraph<*, RepositorySettings>).cacherOf(
                                repoSettings
                            )

                        (loader as DependencyGraph<*, RepositorySettings>.DependencyCacher<ArtifactRequest<*>, *>).cache(depReq).orNull()
                    }
                        ?: shift(ArchiveLoadException.IllegalState("Failed to load dependency: '$dependency' from repositories '${allDependencyRepositories}''"))
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
