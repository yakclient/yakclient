//package net.yakclient.components.extloader.tweaker
//
//import asOutput
//import com.durganmcbroom.artifact.resolver.*
//import com.durganmcbroom.artifact.resolver.simple.maven.*
//import com.durganmcbroom.jobs.JobName
//import com.durganmcbroom.jobs.JobResult
//import com.durganmcbroom.jobs.job
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.module.kotlin.KotlinModule
//import com.fasterxml.jackson.module.kotlin.readValue
//import net.yakclient.archives.ArchiveHandle
//import net.yakclient.archives.ResolutionResult
//import net.yakclient.boot.archive.ArchiveGraph
//import net.yakclient.boot.archive.ArchiveLoadException
//import net.yakclient.boot.archive.ArchiveNode
//import net.yakclient.boot.archive.ArchiveResolutionProvider
//import net.yakclient.boot.dependency.DependencyData
//import net.yakclient.boot.dependency.DependencyGraph
//import net.yakclient.boot.dependency.DependencyGraphProvider
//import net.yakclient.boot.dependency.DependencyNode
//import net.yakclient.boot.maven.MavenDependencyGraph
//import net.yakclient.boot.store.DataStore
//import net.yakclient.components.extloader.extension.ExtensionGraph
//import net.yakclient.components.extloader.extension.artifact.*
//import net.yakclient.components.extloader.util.EmptyResolutionContext
//import net.yakclient.internal.api.environment.EnvironmentAttribute
//import net.yakclient.internal.api.environment.EnvironmentAttributeKey
//import net.yakclient.internal.api.environment.ExtLoaderEnvironment
//import net.yakclient.internal.api.environment.dependencyTypesAttrKey
//import net.yakclient.internal.api.tweaker.EnvironmentTweaker
//import net.yakclient.internal.api.tweaker.TRM_LOCATION
//import net.yakclient.internal.api.tweaker.TweakerRuntimeModel
//import java.nio.file.Path
//
//public data class EnvironmentTweakerNode(
//    val descriptor: SimpleMavenDescriptor,
//    override val archive: ArchiveHandle,
//    val tweaker: EnvironmentTweaker,
//    val trm: TweakerRuntimeModel,
//    override val children: Set<EnvironmentTweakerNode>
//) : ArchiveNode
//
//
//public class EnvironmentTweakerGraph(
//    path: Path,
//    store: DataStore<SimpleMavenDescriptor, DependencyData<SimpleMavenDescriptor>>,
//    private val extRefStore: DataStore<ExtensionDescriptor, List<SimpleMavenDescriptor>>,
//    archiveResolver: ArchiveResolutionProvider<ResolutionResult>,
//    private val environment: ExtLoaderEnvironment
//) : ArchiveGraph<SimpleMavenDescriptor, EnvironmentTweakerNode, SimpleMavenRepositorySettings>(
//    SimpleMaven
//), EnvironmentAttribute {
//    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
//    override val key: EnvironmentAttributeKey<*> = EnvironmentTweakerGraph
//    private val delegateGraph = MavenDependencyGraph(
//        path,
//        store,
//        archiveResolver,
//    )
//    override val graph: MutableMap<SimpleMavenDescriptor, EnvironmentTweakerNode> = HashMap()
//
//    override fun isCached(descriptor: SimpleMavenDescriptor): Boolean {
//        return delegateGraph.isCached(descriptor)
//    }
//
//    override suspend fun get(descriptor: SimpleMavenDescriptor): JobResult<EnvironmentTweakerNode, ArchiveLoadException> =
//        job {
//            fun toTweakerNode(node: DependencyNode): EnvironmentTweakerNode {
//
//                val archive = (node.archive)
//                    ?: fail(ArchiveLoadException.IllegalState("Environment tweaker has a null archive which is not allowed!"))
//
//                val trm = mapper.readValue<TweakerRuntimeModel>(archive.classloader.getResource(TRM_LOCATION)!!)
//                val entrypoint = archive.classloader.loadClass(trm.entrypoint)
//
//                val tweaker = (entrypoint.getConstructor().newInstance() as? EnvironmentTweaker) ?: fail(
//                    ArchiveLoadException.IllegalState("Given tweaker: '${trm.name}' has an entrypoint that does not implement: '${EnvironmentTweaker::class.qualifiedName}'")
//                )
//
//                val tweakerNode = EnvironmentTweakerNode(
//                    descriptor,
//                    node.archive!!,
//                    tweaker,
//                    trm,
//                    node.children.mapTo(HashSet(), ::toTweakerNode)
//                )
//
//                graph[tweakerNode.descriptor] = tweakerNode
//
//                return tweakerNode
//            }
//
//            val dependencyNode = delegateGraph.get(descriptor).attempt()
//
//            toTweakerNode(dependencyNode)
//        }
//
//    override fun cacherOf(settings: SimpleMavenRepositorySettings): ArchiveCacher<SimpleMavenArtifactRequest, *> {
//        return TweakerCacher(delegateGraph.cacherOf(settings))
//    }
//
//
//    public fun tweakerExtCacherFor(settings: ExtensionRepositorySettings): TweakerFromExtensionCacher {
//        val factory = ExtensionRepositoryFactory(environment[dependencyTypesAttrKey]!!)
//        val repo = factory.createNew(settings)
//        return TweakerFromExtensionCacher(
//            SimpleMavenResolutionContext(
//                repo,
//                repo.stubResolver,
//                factory.artifactComposer
//            )
//        )
//    }
//
//    public inner class TweakerFromExtensionCacher(
//        private val extensionArtifactRepository: SimpleMavenResolutionContext,
//    ) {
//        public suspend fun cacheFromExtension(
//            request: ExtensionArtifactRequest
//        ): JobResult<List<EnvironmentTweakerNode>, ArchiveLoadException> = job(JobName("Cache ")) {
//            val descriptors = extRefStore[request.descriptor] ?: run {
//
//                val ref = extensionArtifactRepository.getAndResolve(request).asOutput()
//                    .mapFailure(ArchiveLoadException::ArtifactLoadException).attempt()
//
//                val metadata = ref.metadata as ExtensionArtifactMetadata
//
//
//                val repositories = metadata.erm.environmentTweakerRepositories.map {
//                    val provider =
//                        environment[dependencyTypesAttrKey]?.get(it.type) as? DependencyGraphProvider<SimpleMavenDescriptor, SimpleMavenArtifactRequest, SimpleMavenRepositorySettings>
//                            ?: fail(ArchiveLoadException.DependencyTypeNotFound(it.type))
//
//
//                    (provider.parseSettings(
//                        it.settings
//                    ) ?: fail(
//                        ArchiveLoadException.IllegalState(
//                            "Dependency type: '${it.type}' failed to parse repository settings: '${it.settings}'"
//                        )
//                    )) to provider
//                }
//
//                val descriptors = metadata.erm.environmentTweakers.map {
//                    repositories.firstNotNullOfOrNull { (settings, provider) ->
//                        val tweakerRequest = provider.parseRequest(it)
//                            ?: fail(ArchiveLoadException.IllegalState("Dependency type: '${provider.name}' failed to parse request: '$it'"))
//
//                        cacherOf(settings).cache(tweakerRequest).orNull()?.let {
//                            tweakerRequest.descriptor
//                        }
//                    } ?: fail(ArchiveLoadException.IllegalState("Failed to cache environment tweaker: '$it'"))
//                }
//
//                val allDescriptors = ref.children.flatMap {
//                    val child = it.asOutput().mapFailure { f ->
//                        ArchiveLoadException.ArtifactLoadException(
//                            ArtifactException.ArtifactNotFound(
//                                f.request.descriptor,
//                                f.candidates.map { it.toString() }
//                            )
//                        )
//                    }.attempt()
//
//                    cacheFromExtension(
//                        ExtensionArtifactRequest(
//                            child.metadata.descriptor as ExtensionDescriptor,
//                            request.isTransitive,
//                            request.includeScopes,
//                            request.excludeArtifacts
//                        )
//                    ).attempt().map(EnvironmentTweakerNode::descriptor)
//                } + descriptors
//
//                extRefStore.put(request.descriptor, allDescriptors)
//
//
//                // Explicitly dont want to return child tweakers because when a tweaker is applied it
//                // should apply its dependencies tweakers. However, we do want to return the extensions
//                // childrens tweakers.
//                allDescriptors
//            }
//
//
//            descriptors.map { get(it).attempt() }
//        }
//    }
//
//    public companion object : EnvironmentAttributeKey<EnvironmentTweakerGraph>
//
//    private inner class TweakerCacher(
//        private val delegateCacher: MavenDependencyGraph.MavenDependencyCacher,
//    ) : ArchiveCacher<SimpleMavenArtifactRequest, SimpleMavenArtifactStub>(
//        EmptyResolutionContext()
//    ) {
//        override suspend fun cache(request: SimpleMavenArtifactRequest): JobResult<Unit, ArchiveLoadException> {
//            return delegateCacher.cache(request)
//        }
//
//    }
//
//}