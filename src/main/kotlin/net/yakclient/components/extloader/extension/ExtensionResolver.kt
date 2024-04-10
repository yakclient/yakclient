package net.yakclient.components.extloader.extension

import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.asResourceStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveFinder
import net.yakclient.boot.archive.*
import net.yakclient.boot.maven.MavenLikeResolver
import net.yakclient.boot.util.requireKeyInDescriptor
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactMetadata
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositoryFactory
import java.io.ByteArrayInputStream
import java.nio.file.Files

public open class ExtensionResolver(
    private val finder: ArchiveFinder<*>,
    parent: ClassLoader,
    environment: ExtLoaderEnvironment,
) : MavenLikeResolver<ExtensionNode, ExtensionArtifactMetadata>,
    EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ExtensionResolver
    override val metadataType: Class<ExtensionArtifactMetadata> = ExtensionArtifactMetadata::class.java
    override val nodeType: Class<ExtensionNode> = ExtensionNode::class.java
    override val name: String = "extension"

    private val factory = ExtensionRepositoryFactory(environment[dependencyTypesAttrKey].extract().container)
//    private val applicationReference = environment[ApplicationTarget].extract().reference
//    private val dependencyProviders = environment[dependencyTypesAttrKey].extract().container
    private val referenceLoader = ExtensionContainerLoader(parent, environment)
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
//    private val archiveGraph = environment.archiveGraph
//    private val target = environment[TargetLinker]

    public companion object : EnvironmentAttributeKey<ExtensionResolver>

    override fun createContext(settings: SimpleMavenRepositorySettings): ResolutionContext<SimpleMavenArtifactRequest, *, ExtensionArtifactMetadata, *> {
        return factory.createContext(settings)
    }

//    private data class ExtraDependencyInfo(
//        val descriptor: Map<String, String>,
//        val resolver: String,
//        val partition: String,
//    )

    override fun load(
        data: ArchiveData<ExtensionDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<ExtensionNode> = job {
        val erm =
            data.resources.requireKeyInDescriptor("erm.json") { trace() }.path.let {
                mapper.readValue<ExtensionRuntimeModel>(
                    it.toFile()
                )
            }

        val reference = data.resources["jar.jar"]
            ?.path
            ?.takeIf(Files::exists)
            ?.let(finder::find)
//            ?.let {
//                VersionedExtErmArchiveReference(
//                    it, applicationReference.descriptor.version, erm
//                )
//            }

//        val extraDependencyInfo =
//            mapper.readValue<List<ExtraDependencyInfo>>(data.resources["extra-dep-info.json"]!!.path.toFile())

        val parents = runBlocking {
//            val dependencies = extraDependencyInfo
////                .filter { (_, _, partitionName) ->
////                    if (partitionName == "main" || partitionName == "tweaker") true
////                    else {
////                        erm.partitions.find {
////                            it.name == partitionName
////                        }?.supportedVersions?.contains(environment[ApplicationTarget].extract().reference.descriptor.version)
////                            ?: false
////                    }
////                }
//            .map { (dependency, resolver) ->
//                    val dependencyResolver = helper.getResolver(
//                        resolver,
//                        ArtifactMetadata.Descriptor::class.java,
//                        DependencyNode::class.java as Class<out DependencyNode<*>>
//                    ).merge()
//                    val desc = dependencyResolver.deserializeDescriptor(dependency, trace()).merge()
//
//                    async {
//                        helper.load(
//                            desc,
//                            dependencyResolver
//                        )
//                    }
//                }

            val parents = data.parents.map {
                async {
                    helper.load(
                        it.descriptor,
                        this@ExtensionResolver
                    )
                }
            }


            parents.awaitAll()
        }

//        val tweakerDescriptor = data.descriptor.copy(
//            classifier = "tweaker"
//        )

//        val tweakerNode =
//            archiveGraph[tweakerDescriptor]

//        val access = helper.newAccessTree {
////            allDirect(dependencies)
////            if (tweakerNode != null) direct(tweakerNode)
//
////            rawTarget(
////                target.map { it.targetTarget }.defer(applicationReference.descriptor)
////            )
//            parents.forEach {
//                rawTarget(ArchiveTarget(
//                    it.descriptor,
//                    object : ArchiveRelationship {
//                        override val name: String = "Lazy Direct"
//                        override val classes: ClassProvider by lazy {
//                            ArchiveClassProvider(it.archive)
//                        }
//
//                        override val resources: ResourceProvider by lazy {
//                            ArchiveResourceProvider(it.archive)
//                        }
//                    }
//                ))
//            }
//        }

        val (extRef, partitions) = if (reference != null) referenceLoader.load(
            reference,
            parents,
            erm,
        )().merge() else null to listOf()

        ExtensionNode(
            data.descriptor,
            extRef,
            partitions,
            erm,
            parents.toSet(),
            this@ExtensionResolver
        )
    }

    override fun cache(
        metadata: ExtensionArtifactMetadata,
        helper: ArchiveCacheHelper<ExtensionDescriptor>
    ): Job<ArchiveData<ExtensionDescriptor, CacheableArchiveResource>> = job {
//        val erm = metadata.erm

//        val allDependencies =
//            erm.partitions
//                .flatMap { p -> p.dependencies.map { it to p } }
//                .map { (dependency, p) ->
//                    val trace = trace()
//
//                    var dependencyDescriptor: ArtifactMetadata.Descriptor? = null
//                    var dependencyResolver: ArchiveNodeResolver<*, *, *, *, *>? = null
//
//                    p.repositories.firstNotFailureOf findRepo@ { settings ->
//                        val provider: DependencyResolverProvider<*, *, *> =
//                            dependencyProviders.get(settings.type) ?: throw ArchiveException.ArchiveTypeNotFound(
//                                settings.type,
//                                trace
//                            )
//
//                        val depReq: ArtifactRequest<*> = provider.parseRequest(dependency) ?: casuallyFail(
//                            ArchiveException.DependencyInfoParseFailed("Failed to parse request: '$dependency'", trace)
//                        )
//
//                        val repoSettings = provider.parseSettings(settings.settings) ?: casuallyFail(
//                            ArchiveException.DependencyInfoParseFailed("Failed to parse settings: '$settings'", trace)
//                        )
//
//                        dependencyDescriptor = depReq.descriptor
//                        dependencyResolver = provider.resolver
//
//                        helper.cache(
//                            depReq as ArtifactRequest<ArtifactMetadata.Descriptor>,
//                            repoSettings,
//                            provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>
//                        )()
//                    }.mapException {
//                        ArchiveException(
//                            trace,
//                            "Failed to load dependency: '$dependency' from repositories '${p.repositories}'. Error was: '$it'.",
//                        )
//                    }.merge()
//
//                    Triple(dependencyDescriptor!!, dependencyResolver!!, p)
//                }

        helper.withResource("jar.jar", metadata.resource)
        helper.withResource(
            "erm.json",
            DelegatingResource("no location provided") {
                // TODO wrap in job catching block for extra safety if object mapper fails (should never happen though)
                ByteArrayInputStream(mapper.writeValueAsBytes(metadata.erm)).asResourceStream()
            }
        )

//        helper.withResource(
//            "extra-dep-info.json",
//            DelegatingResource("no location provided") {
//                ByteArrayInputStream(
//                    mapper.writeValueAsBytes(
//                        allDependencies.map { (descriptor, resolver, partition) ->
//                            val serializedDescriptor =
//                                (resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>).serializeDescriptor(
//                                    descriptor
//                                )
//
//                            ExtraDependencyInfo(
//                                serializedDescriptor,
//                                resolver.name,
//                                partition.name
//                            )
//                        }
//                    )).asResourceStream()
//            }
//        )

        helper.newData(metadata.descriptor)
    }
}
