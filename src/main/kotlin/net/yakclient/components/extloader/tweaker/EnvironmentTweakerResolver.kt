package net.yakclient.components.extloader.tweaker

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.asResourceStream
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
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactMetadata
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveHandle
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveReference
import net.yakclient.components.extloader.tweaker.archive.TweakerClassLoader
import net.yakclient.components.extloader.tweaker.artifact.TweakerRepositoryFactory
import java.io.ByteArrayInputStream
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
) : MavenLikeResolver<EnvironmentTweakerNode, SimpleMavenArtifactMetadata>,
    EnvironmentAttribute {
    private val dependencyProviders = environment[dependencyTypesAttrKey].extract().container
    private val factory = TweakerRepositoryFactory(dependencyProviders)
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val name: String = "environment-tweaker"
    override val nodeType: Class<EnvironmentTweakerNode> = EnvironmentTweakerNode::class.java
    override fun createContext(settings: SimpleMavenRepositorySettings): ResolutionContext<SimpleMavenArtifactRequest, *, SimpleMavenArtifactMetadata, *> {
        return factory.createContext(settings)
    }

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun pathForDescriptor(descriptor: SimpleMavenDescriptor, classifier: String, type: String): Path {
        return Path.of("tweakers") resolve super.pathForDescriptor(descriptor, classifier, type)
    }

    override fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<EnvironmentTweakerNode> = job {
        val erm =
            mapper.readValue<ExtensionRuntimeModel>(data.resources.requireKeyInDescriptor("erm.json") {trace()}.path.toFile())
        val jar = data.resources["tweaker.jar"]?.path
        val tweakerPartition = erm.tweakerPartition
//            ?: throw ArchiveException.IllegalState(
//                "Extension '${data.descriptor}' does not have a tweaker yet you are trying to load it!",
//                trace()
//            )

        val parents = data.parents.map {
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
            )
                .merge() //dependencyProviders.get(it.resolver)?.resolver ?: raise(ArchiveException.ArchiveTypeNotFound(it.resolver, trace()))

            helper.load(
                localResolver.deserializeDescriptor(descriptor, trace()).merge(),
                localResolver
//                localResolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, DependencyNode, *, *, *>
            )
        }

        val access = helper.newAccessTree {
            allDirect(dependencies)
            allDirect(parents)
        }


        val ref = jar?.let { path ->
            tweakerPartition?.let { part ->
                TweakerArchiveReference(
                    erm.name,
                    part.path.removeSuffix("/") + "/",
                    Archives.find(path, Archives.Finders.ZIP_FINDER)
                )
            }
        }

        fun handleOrParents(node: ArchiveNode<*>): List<ArchiveHandle> =
            node.archive?.let(::listOf) ?: node.parents.flatMap { handleOrParents(it) }

        val archive = ref?.let {
            TweakerArchiveHandle(
                erm.name + "-tweaker",
                TweakerClassLoader(it, access, parent),
                it,
                parents.flatMapTo(HashSet(), ::handleOrParents)
            )
        }

        val entrypoint = archive?.classloader?.loadClass(tweakerPartition!!.entrypoint)

        val tweaker = entrypoint?.getConstructor()?.newInstance()?.let {
            it as? EnvironmentTweaker ?: throw ArchiveException(
                trace(),
                "Given extension: '${erm.name}' has a tweaker that does not implement: '${EnvironmentTweaker::class.qualifiedName}'",
            )
        }

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

    override fun cache(
        metadata: SimpleMavenArtifactMetadata,
        helper: ArchiveCacheHelper<SimpleMavenDescriptor>
    ): Job<ArchiveData<SimpleMavenDescriptor, CacheableArchiveResource>> = job {
        metadata as ExtensionArtifactMetadata
        val trace = trace()

        val tweakerPartition = metadata.erm.tweakerPartition

        val dependencies = tweakerPartition?.dependencies?.map { req ->
            var dependencyDescriptor: ArtifactMetadata.Descriptor? = null
            var dependencyResolver: ArchiveNodeResolver<*, *, *, *, *>? = null

            tweakerPartition.repositories.firstNotFailureOf {
                val p = dependencyProviders.get(it.type) ?: throw ArchiveException.ArchiveTypeNotFound(it.type, trace)

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
                    (p.parseSettings(it.settings) ?: casuallyFail(
                        ArchiveException.DependencyInfoParseFailed(
                            "Could not parse settings: '${it.settings}'",
                            trace
                        )
                    )),
                    p.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>
                )()
            }.merge()

            dependencyDescriptor!! to dependencyResolver!!
        } ?: listOf()

        helper.withResource("tweaker.jar", metadata.resource)
        helper.withResource("erm.json", DelegatingResource("http://nothing") {
            ByteArrayInputStream(mapper.writeValueAsBytes(metadata.erm)).asResourceStream()
        })
        helper.withResource(
            "extra-dep-info.json",
            DelegatingResource("http://nothing") {
                ByteArrayInputStream(mapper.writeValueAsBytes(
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
                )).asResourceStream()
            }
        )

        helper.newData(metadata.descriptor)
    }

    private val parent = environment[ParentClassloaderAttribute].extract().cl

    override val key: EnvironmentAttributeKey<*> = EnvironmentTweakerResolver

    public companion object : EnvironmentAttributeKey<EnvironmentTweakerResolver>
}