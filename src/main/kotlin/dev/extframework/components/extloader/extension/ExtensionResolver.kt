package dev.extframework.components.extloader.extension

import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.DelegatingResource
import com.durganmcbroom.resources.asResourceStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import dev.extframework.archives.ArchiveFinder
import dev.extframework.boot.archive.*
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.boot.util.requireKeyInDescriptor
import dev.extframework.components.extloader.api.environment.*
import dev.extframework.components.extloader.api.extension.ExtensionRuntimeModel
import dev.extframework.components.extloader.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.components.extloader.extension.artifact.ExtensionDescriptor
import dev.extframework.components.extloader.extension.artifact.ExtensionRepositoryFactory
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
    private val referenceLoader = ExtensionContainerLoader(parent, environment)
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    public companion object : EnvironmentAttributeKey<ExtensionResolver>

    override fun createContext(settings: SimpleMavenRepositorySettings): ResolutionContext<SimpleMavenArtifactRequest, *, ExtensionArtifactMetadata, *> {
        return factory.createContext(settings)
    }

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
        val parents = runBlocking {
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
        helper.withResource("jar.jar", metadata.resource)
        helper.withResource(
            "erm.json",
            DelegatingResource("no location provided") {
                // TODO wrap in job catching block for extra safety if object mapper fails (should never happen though)
                runCatching {
                    ByteArrayInputStream(mapper.writeValueAsBytes(metadata.erm)).asResourceStream()
                }.mapException {
                    ExtensionLoadException(metadata.descriptor, it) {}
                }.merge()
            }
        )
        helper.newData(metadata.descriptor)
    }
}
