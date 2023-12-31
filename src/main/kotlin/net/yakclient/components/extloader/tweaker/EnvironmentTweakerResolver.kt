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
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveHandle
import net.yakclient.components.extloader.tweaker.archive.TweakerArchiveReference
import net.yakclient.components.extloader.tweaker.archive.TweakerClassLoader
import net.yakclient.components.extloader.tweaker.artifact.TweakerRepositoryFactory
import java.net.URI
import java.nio.file.Path
import kotlin.reflect.KClass

public data class EnvironmentTweakerNode(
    override val descriptor: SimpleMavenDescriptor,
    override val archive: ArchiveHandle?,
    val tweaker: EnvironmentTweaker?,
    override val children: Set<EnvironmentTweakerNode>
) : ArchiveNode<EnvironmentTweakerNode>

public class EnvironmentTweakerResolver(
    environment: ExtLoaderEnvironment
) : MavenLikeResolver<SimpleMavenArtifactRequest, EnvironmentTweakerNode, SimpleMavenRepositorySettings, SimpleMavenRepositoryStub, SimpleMavenArtifactMetadata>,
    EnvironmentAttribute {
    private val dependencyProviders = environment[dependencyTypesAttrKey]!!.container
    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, *, ArtifactReference<SimpleMavenArtifactMetadata, *>, *> =
        TweakerRepositoryFactory(dependencyProviders)
    override val metadataType: KClass<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class
    override val name: String = "environment-tweaker"
    override val nodeType: KClass<EnvironmentTweakerNode> = EnvironmentTweakerNode::class

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun pathForDescriptor(descriptor: SimpleMavenDescriptor, classifier: String, type: String): Path {
        return Path.of("tweakers") resolve super.pathForDescriptor(descriptor, classifier, type)
    }

    override suspend fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        resolver: ChildResolver
    ): JobResult<EnvironmentTweakerNode, ArchiveException> = jobScope {
        val erm = mapper.readValue<ExtensionRuntimeModel>(data.resources.requireKeyInDescriptor("erm.json").path.toFile())
        val jar = data.resources.requireKeyInDescriptor("tweaker.jar").path
        val tweakerPartition = erm.tweakerPartition
            ?: fail(ArchiveException.IllegalState("Extension '${data.descriptor}' does not have a tweaker yet you are trying to load it!", trace()))

        val (unresolvedChildren, unresolvedDependencies) = data.children.partition { it.resolver == name }

        val children = unresolvedChildren.map {
            resolver.load(
                it.descriptor as ExtensionDescriptor,
                this@EnvironmentTweakerResolver
            )
        }
        val dependencies = unresolvedDependencies.map {
            val localResolver = dependencyProviders.get(it.resolver)?.resolver ?: fail(ArchiveException.ArchiveTypeNotFound(it.resolver, trace()))

            resolver.load(
                it.descriptor,
                localResolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, DependencyNode, *, *, *>
            )
        }

        val ref = TweakerArchiveReference(
            tweakerPartition.path.removeSuffix("/") + "/",
            Archives.find(jar, Archives.Finders.ZIP_FINDER)
        )

        val archiveDependencies: Set<ArchiveHandle> =
            dependencies.flatMapTo(HashSet()) { it.handleOrChildren() } +
                    children.mapNotNull { it.archive }

        val archive = TweakerArchiveHandle(
            erm.name + "-tweaker",
            TweakerClassLoader(ref, archiveDependencies, parent),
            ref,
            archiveDependencies.toSet()
        )

        val entrypoint = archive.classloader.loadClass(tweakerPartition.entrypoint)

        val tweaker = (entrypoint.getConstructor().newInstance() as? EnvironmentTweaker) ?: fail(
            ArchiveException.IllegalState("Given extension: '${erm.name}' has a tweaker that does not implement: '${EnvironmentTweaker::class.qualifiedName}'", trace())
        )

        EnvironmentTweakerNode(
            data.descriptor,
            archive,
            tweaker,
            children.toSet()
        )
    }

    override suspend fun cache(
        ref: ArtifactReference<SimpleMavenArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>,
        helper: ArchiveCacheHelper<SimpleMavenRepositoryStub, SimpleMavenRepositorySettings>
    ): JobResult<ArchiveData<SimpleMavenDescriptor, CacheableArchiveResource>, ArchiveException> = jobScope {
        val metadata = ref.metadata as ExtensionArtifactMetadata
        val trace = trace()

        val tweakerPartition = metadata.erm.tweakerPartition

        val children = ref.children.map { stub ->
            stub.candidates.firstNotFailureOf { candidate ->
                helper.cache(
                    stub.request.withNewDescriptor(
                        stub.request.descriptor.copy(classifier = "tweaker")
                    ),
                    helper.resolve(candidate).attempt(),
                    this@EnvironmentTweakerResolver,
                )
            }.attempt()
        }

        val dependencies = tweakerPartition?.dependencies?.map { req ->
            tweakerPartition.repositories.firstNotFailureOf {
                val p = dependencyProviders.get(it.type) ?: fail(ArchiveException.ArchiveTypeNotFound(it.type, trace))

                helper.cache(
                    (p.parseRequest(req)
                        ?: casuallyFail(ArchiveException.DependencyInfoParseFailed("Could not parse request: '$req'.",
                            trace
                        ))) as ArtifactRequest<ArtifactMetadata.Descriptor>,
                    (p.parseSettings(it.settings) ?: casuallyFail(ArchiveException.DependencyInfoParseFailed("Could not parse settings: '${it.settings}'",
                        trace
                    ))),
                    p.resolver as DependencyResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, RepositorySettings, RepositoryStub, *>
                )
            }.mapFailure {
                ArchiveException.IllegalState("Failed to load dependency: '$req' from repositories '${tweakerPartition.repositories}'. Error was: '${it.message}'",
                    trace
                )
            }.attempt()
        } ?: listOf()

        ArchiveData(
            ref.metadata.descriptor,
            mapOfNonNullValues(
                "tweaker.jar" to ref.metadata.resource?.toSafeResource()?.let(::CacheableArchiveResource),
                "erm.json" to CacheableArchiveResource(ProvidedResource(URI.create("http://nothing")) {
                    mapper.writeValueAsBytes(metadata.erm)
                })
            ),
            dependencies + children
        )
    }

    private val parent = environment[ParentClassloaderAttribute]!!.cl

    override val key: EnvironmentAttributeKey<*> = EnvironmentTweakerResolver

    public companion object : EnvironmentAttributeKey<EnvironmentTweakerResolver>
}