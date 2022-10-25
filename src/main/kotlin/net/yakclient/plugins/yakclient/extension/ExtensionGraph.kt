package net.yakclient.plugins.yakclient.extension

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import arrow.core.right
import com.durganmcbroom.artifact.resolver.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveResolver
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.handleOrChildren
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerLoader
import net.yakclient.boot.container.volume.RootVolume
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.DependencyProviders
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.plugins.yakclient.extension.artifact.*
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

//
//public abstract class ExtensionGraph<N : ExtensionNode, K: ArchiveKey, D : ExtensionData>(
//    store: ExtensionStore<K, D>
//) : RepositoryArchiveGraph<N, K, D>(store) {
//    private val _graph: MutableMap<K, N> = HashMap()
//    override val graph: Map<K, N>
//        get() = _graph.toMap()
//
////    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
////        provider: ArtifactGraphProvider<C, ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
////        config: C.() -> Unit
////    ): RepositoryConfigurer<S, O> = createLoader(provider, provider.emptyConfig().apply(config))
////
////    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
////        provider: ArtifactGraphProvider<C, ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
////        config: C,
////    ): RepositoryConfigurer<S, O> = createLoader(
////        provider.provide(config.also { it.graph = controller }),
////    )
////
////    public fun <S : RepositorySettings, O : ArtifactResolutionOptions> createLoader(
////        graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
////    ): RepositoryConfigurer<S, O> = RepositoryConfigurer(graph)
////
////    public fun <O : ArtifactResolutionOptions> createLoader(resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>): RepositoryGraphPopulator<O> =
////        DependencyGraphPopulator(resolver)
//
////    public inner class RepositoryConfigurer<S : RepositorySettings, O : ArtifactResolutionOptions>(
////        private val graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
////    ) {
////        public fun configureRepository(settings: S.() -> Unit): RepositoryGraphPopulator<O> =
////            configureRepository(graph.newRepoSettings().apply(settings))
////
////        public fun configureRepository(settings: S): RepositoryGraphPopulator<O> =
////            createLoader(graph.resolverFor(settings))
////    }
//
//
//    public abstract inner class ExtensionGraphPopulator<O : ArtifactResolutionOptions, T : ExtensionInfo>(
//        resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>, protected val loader: ExtensionLoader<T>
//    ) : RepositoryGraphPopulator<O>(resolver)
//}

private val ERM_PATH = "META-INF/erm.json"

public class ExtensionGraph(
    private val path: Path,
    private val finder: ArchiveFinder<*>,
    resolver: ArchiveResolver<*, *>,
    private val privilegeManager: PrivilegeManager,
    parent: ClassLoader,
) : ArchiveGraph<ExtensionArtifactRequest, ExtensionNode, ExtensionRepositorySettings>(
    ExtensionRepositoryFactory
) {
    private val store = CachingDataStore(ExtensionDataAccess(path))
    private val extProcessLoader = ExtensionProcessLoader(
        privilegeManager,
        parent,
        resolver
    )
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val mutableGraph: MutableMap<ExtensionDescriptor, ExtensionNode> = HashMap()
    override val graph: Map<ExtensionDescriptor, ExtensionNode>
        get() = mutableGraph.toMap()

    override fun loaderOf(settings: ExtensionRepositorySettings): ArchiveLoader<*> {
        return ExtensionLoader(ExtensionRepositoryFactory.createContext(settings))
    }

    private fun getBasePathFor(desc: ExtensionDescriptor): Path =
        path resolve desc.group.replace(
            '.',
            File.separatorChar
        ) resolve desc.artifact resolve desc.version

    private fun getJarPathFor(desc: ExtensionDescriptor): Path =
        getBasePathFor(desc) resolve "${desc.artifact}-${desc.version}.jar"

    private fun getErmPathFor(desc: ExtensionDescriptor): Path =
        getBasePathFor(desc) resolve "${desc.artifact}-${desc.version}-erm.json"

    override fun get(request: ExtensionArtifactRequest): Either<ArchiveLoadException, ExtensionNode> {
        return graph[request.descriptor]?.right() ?: either.eager {
            val path = getJarPathFor(request.descriptor)

            val ref = path.takeIf(Files::exists)?.let(finder::find)

            val ermPath = ensureNotNull(getErmPathFor(request.descriptor).takeIf(Files::exists)) {
                ArchiveLoadException.IllegalState("Extension runtime model for request: '${request.descriptor}' not found cached.")
            }

            val erm: ExtensionRuntimeModel = mapper.readValue(ermPath.toFile())

            val children: List<ExtensionNode> = erm.extensions.map {
                val extRequest = DependencyProviders["simple-maven"]!!.parseRequest(it)
                    ?: shift(ArchiveLoadException.IllegalState("Illegal extension request: '$it'"))
                get(extRequest as ExtensionArtifactRequest).bind()
            }

            val dependencies = erm.dependencies.map { dep ->
                val find = erm.dependencyRepositories.firstNotNullOfOrNull find@{ repo ->
                    val provider = DependencyProviders[repo.type]
                        ?: shift(ArchiveLoadException.DependencyTypeNotFound(repo.type))
                    val depReq = provider.parseRequest(dep) ?: return@find null

                    (provider.graph as DependencyGraph<ArtifactRequest<*>, *, *>).get(depReq).orNull()
                }
                find ?: shift(ArchiveLoadException.IllegalState("Couldnt load dependency: '${dep}' for extension: '${request.descriptor}'"))

            }


            val containerHandle = ContainerLoader.createHandle<ExtensionProcess>()

            fun containerOrChildren(node: ExtensionNode): List<Container<ExtensionProcess>> =
                node.extension?.let(::listOf) ?: children.flatMap { containerOrChildren(it) }

            val container = if (ref != null) ContainerLoader.load(
                ExtensionInfo(
                    ref,
                    children.flatMap(::containerOrChildren),
                    dependencies.flatMap { it.handleOrChildren() },
                    erm
                ),
                containerHandle,
                extProcessLoader,
                RootVolume.derive(erm.name, getBasePathFor(request.descriptor)),
                privilegeManager
            ) else null

            ExtensionNode(
                container?.handle,
                children.toSet(),
                dependencies.toSet(),
                container
            ).also {
                mutableGraph[request.descriptor] = it
            }
        }
    }

    private inner class ExtensionLoader(
        resolver: ResolutionContext<ExtensionArtifactRequest, ExtensionStub, ArtifactReference<*, ExtensionStub>>,
    ) : ArchiveLoader<ExtensionStub>(
        resolver
    ) {
        private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        override fun load(request: ExtensionArtifactRequest): Either<ArchiveLoadException, ExtensionNode> {
            val desc = request.descriptor
            return graph[request.descriptor]?.right() ?: either.eager {
                val path = getJarPathFor(desc)
                if (!path.exists()) {
                    val ref = resolver.repositoryContext.artifactRepository.get(request)
                        .mapLeft(ArchiveLoadException::ArtifactLoadException)
                        .bind()

                    cache(request, ref as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>).bind()
                }

                get(request).bind()
            }
        }

        private fun cache(
            request: ExtensionArtifactRequest,
            ref: ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>,
        ): Either<ArchiveLoadException, Unit> = either.eager {
            ref.children.forEach { stub ->
                val resolve = resolver.resolverContext.stubResolver
                    .resolve(stub)

                val childRef = resolve
                    .mapLeft(ArchiveLoadException::ArtifactLoadException)
                    .bind() as ArtifactReference<ExtensionArtifactMetadata, ExtensionArtifactStub>

                cache(stub.request, childRef).bind()
            }

            val metadata = ref.metadata
            metadata.erm.dependencies.forEach { dependency ->
                metadata.erm.dependencyRepositories.firstNotNullOfOrNull findRepo@{ settings ->
                    val provider = DependencyProviders[settings.type]
                        ?: return@findRepo null

                    val depReq = provider.parseRequest(dependency)
                        ?: return@findRepo null

                    val repoSettings = provider.parseSettings(settings.settings)
                        ?: return@findRepo null

                    val loader =
                        (provider.graph as DependencyGraph<ArtifactRequest<*>, *, RepositorySettings>).loaderOf(
                            repoSettings
                        )

                    (loader as DependencyGraph.DependencyLoader).cache(depReq).bind()
                } ?: shift(ArchiveLoadException.IllegalState("Failed to load dependency: '$dependency' from repositories '${metadata.erm.dependencyRepositories}''"))
            }

            val jarPath = getJarPathFor(request.descriptor)

            val ermPath = getErmPathFor(request.descriptor)
            if (!Files.exists(ermPath)) ermPath.make()

            ermPath.writeBytes(mapper.writeValueAsBytes(metadata.erm))

            val resource = metadata.resource
            if (resource != null) {
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
