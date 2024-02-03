package net.yakclient.components.extloader.tweaker

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.jobScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.Archives
import net.yakclient.boot.archive.*
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.maven.MavenLikeResolver
import net.yakclient.boot.util.*
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.ProvidedResource
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactMetadata
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveHandle
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveReference
import net.yakclient.components.extloader.tweaker.archive.TweakerClassLoader
import net.yakclient.components.extloader.tweaker.artifact.TweakerRepositoryFactory
import java.net.URI
import java.nio.file.Path

public data class EnvironmentTweakerNode(
    override val descriptor: SimpleMavenDescriptor,
    override val archive: ArchiveHandle?,
    val tweaker: EnvironmentTweaker?,
    override val parents: Set<EnvironmentTweakerNode>,
    override val access: ArchiveAccessTree,
    override val resolver: ArchiveNodeResolver<*, *, EnvironmentTweakerNode, *, *>
) : ArchiveNode<EnvironmentTweakerNode>

public class EnvironmentTweakerResolver(
    environment: ExtLoaderEnvironment
) : MavenLikeResolver<SimpleMavenArtifactRequest, EnvironmentTweakerNode, SimpleMavenRepositorySettings, SimpleMavenArtifactMetadata>,
    EnvironmentAttribute {
    private val dependencyProviders = environment[dependencyTypesAttrKey]!!.container
    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, *, ArtifactReference<SimpleMavenArtifactMetadata, *>, *> = TweakerRepositoryFactory(dependencyProviders)
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val name: String = "environment-tweaker"
    override val nodeType: Class<EnvironmentTweakerNode> = EnvironmentTweakerNode::class.java

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun pathForDescriptor(descriptor: SimpleMavenDescriptor, classifier: String, type: String): Path {
        return Path.of("tweakers") resolve super.pathForDescriptor(descriptor, classifier, type)
    }

    override suspend fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): JobResult<EnvironmentTweakerNode, ArchiveException>  = jobScope {
        val erm = mapper.readValue<ExtensionRuntimeModel>(data.resources.requireKeyInDescriptor("erm.json").path.toFile())
        val jar = data.resources.requireKeyInDescriptor("tweaker.jar").path
        val tweakerPartition = erm.tweakerPartition
            ?: fail(ArchiveException.IllegalState("Extension '${data.descriptor}' does not have a tweaker yet you are trying to load it!", trace()))

        val parents = data.parents .map {
            helper.load(
                it.descriptor,
                this@EnvironmentTweakerResolver
            )
        }

        val extraDependencyInfo =
            mapper.readValue<List<ExtraDependencyInfo>>(data.resources["extra-dep-info.json"]!!.path.toFile())


        val dependencies = extraDependencyInfo.map { (descriptor, resolver) ->
            val localResolver = helper.getResolver(
                resolver,
                ArtifactMetadata.Descriptor::class.java,
                DependencyNode::class.java as Class<out DependencyNode<*>>
            ).attempt() //dependencyProviders.get(it.resolver)?.resolver ?: fail(ArchiveException.ArchiveTypeNotFound(it.resolver, trace()))

            helper.load(
                localResolver.deserializeDescriptor(descriptor).attempt(),
                localResolver
//                localResolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, DependencyNode, *, *, *>
            )
        }

        val access = helper.newAccessTree {
            allDirect(dependencies)
            allDirect(parents)
        }


        val ref = TweakerArchiveReference(
            erm.name,
            tweakerPartition.path.removeSuffix("/") + "/",
            Archives.find(jar, Archives.Finders.ZIP_FINDER)
        )

        fun handleOrParents(node: ArchiveNode<*>): List<ArchiveHandle> =
            node.archive?.let(::listOf) ?: node.parents.flatMap { handleOrParents(it) }

        val archive = TweakerArchiveHandle(
            erm.name + "-tweaker",
            TweakerClassLoader(ref, access, parent),
            ref,
            parents.flatMapTo(HashSet(), ::handleOrParents)
        )

        val entrypoint = archive.classloader.loadClass(tweakerPartition.entrypoint)

        val tweaker = (entrypoint.getConstructor().newInstance() as? EnvironmentTweaker) ?: fail(
            ArchiveException.IllegalState("Given extension: '${erm.name}' has a tweaker that does not implement: '${EnvironmentTweaker::class.qualifiedName}'", trace())
        )

        EnvironmentTweakerNode(
            data.descriptor,
            archive,
            tweaker,
            parents.toSet(),
            access,
            this@EnvironmentTweakerResolver
        )
    }


    private data class ExtraDependencyInfo(
        val descriptor: Map<String, String>,
        val resolver: String,
    )
    override suspend fun cache(
        metadata: SimpleMavenArtifactMetadata,
        helper: ArchiveCacheHelper<SimpleMavenDescriptor>
    ): JobResult<ArchiveData<SimpleMavenDescriptor, CacheableArchiveResource>, ArchiveException> = jobScope {
        metadata as ExtensionArtifactMetadata
        val trace = trace()

        val tweakerPartition = metadata.erm.tweakerPartition


//        val children = children.map { stub ->
//            stub.candidates.firstNotFailureOf { candidate ->
//                helper.cache(
//                    stub.request.withNewDescriptor(
//                        stub.request.descriptor.copy(classifier = "tweaker")
//                    ),
//                    helper.resolve(candidate).attempt(),
//                    this@EnvironmentTweakerResolver,
//                )
//            }.attempt()
//        }

        val dependencies = tweakerPartition?.dependencies?.map { req ->
            var dependencyDescriptor: ArtifactMetadata.Descriptor? = null
            var dependencyResolver: ArchiveNodeResolver<*, *, *, *, *>? = null

             tweakerPartition.repositories.firstNotFailureOf {
                val p = dependencyProviders.get(it.type) ?: fail(ArchiveException.ArchiveTypeNotFound(it.type, trace))


                val artifactRequest = (p.parseRequest(req)
                    ?: casuallyFail(
                        ArchiveException.DependencyInfoParseFailed(
                            "Could not parse request: '$req'.",
                            trace
                        )
                    ))

                dependencyDescriptor = artifactRequest.descriptor
                dependencyResolver = p.resolver

                helper.cache(
                    artifactRequest as ArtifactRequest<ArtifactMetadata.Descriptor>,
                    (p.parseSettings(it.settings) ?: casuallyFail(ArchiveException.DependencyInfoParseFailed("Could not parse settings: '${it.settings}'",
                        trace
                    ))),
                    p.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>
                )
            }.mapFailure {
                ArchiveException.IllegalState("Failed to load dependency: '$req' from repositories '${tweakerPartition.repositories}'. Error was: '${it.message}'",
                    trace
                )
            }.attempt()

            dependencyDescriptor!! to dependencyResolver!!
        } ?: listOf()

        helper.withResource("tweaker.jar", metadata.resource?.toSafeResource())
        helper.withResource("erm.json", ProvidedResource(URI.create("http://nothing")) {
            mapper.writeValueAsBytes(metadata.erm)
        })
        helper.withResource(
            "extra-dep-info.json",
            ProvidedResource(URI.create("http://nothing")) {
                mapper.writeValueAsBytes(
                    dependencies.map { (descriptor, resolver) ->
                        val serializedDescriptor =
                            (resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>).serializeDescriptor(
                                descriptor
                            )

                        ExtraDependencyInfo(
                            serializedDescriptor,
                            resolver.name,
                        )
                    }
                )
            }
        )

        helper.newData(metadata.descriptor)

//        ArchiveData(
//            ref.metadata.descriptor,
//            mapOfNonNullValues(
//                "tweaker.jar" to ref.metadata.resource?.toSafeResource()?.let(::CacheableArchiveResource),
//                "erm.json" to CacheableArchiveResource(ProvidedResource(URI.create("http://nothing")) {
//                    mapper.writeValueAsBytes(metadata.erm)
//                })
//            ),
//            dependencies + children
//

    }

    private val parent = environment[ParentClassloaderAttribute]!!.cl

    override val key: EnvironmentAttributeKey<*> = EnvironmentTweakerResolver

    public companion object : EnvironmentAttributeKey<EnvironmentTweakerResolver>
}