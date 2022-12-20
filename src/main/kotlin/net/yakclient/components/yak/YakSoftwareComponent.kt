package net.yakclient.components.yak

import arrow.core.continuations.either
import arrow.core.handleErrorWith
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import net.yakclient.archive.mapper.MappedArchive
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.mixin.*
import net.yakclient.archives.transform.ProvidedInstructionReader
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.component.SoftwareComponent
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.components.yak.extension.ExtensionContext
import net.yakclient.components.yak.extension.ExtensionGraph
import net.yakclient.components.yak.extension.ExtensionNode
import net.yakclient.components.yak.extension.artifact.ExtensionArtifactRequest
import net.yakclient.components.yak.extension.artifact.ExtensionRepositorySettings
import net.yakclient.components.yak.mapping.MappingProvider
import net.yakclient.components.yak.mapping.ProGuardMappingParser
import net.yakclient.components.yak.mixin.MixinInjectionProvider
import net.yakclient.minecraft.bootstrapper.MinecraftBootstrapper
import net.yakclient.minecraft.bootstrapper.MixinMetadata
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger
import net.yakclient.components.yak.mapping.*;
import net.yakclient.components.yak.mixin.InjectionPoints
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

public class YakSoftwareComponent : SoftwareComponent {
    private lateinit var graph: ExtensionGraph
    private val extensions = ArrayList<ExtensionNode>()


    override fun onEnable(context: ComponentContext) {
        val cache by context.configuration
        val configExtensions = context.configuration["extensions"] ?: ""

        // net.yakclient:example:example->maven.yakclient.net@default;another
        val toLoad = configExtensions
            .takeUnless(String::isEmpty)
            ?.split(";")
            ?.associate { e ->
                val (descriptor, repo) = e.split("->")
                    .takeIf { it.size == 2 }
                    ?: throw IllegalArgumentException("Invalid extension specified. Found: '$e' but format should be '<DESCRIPTOR>-><REPO>@<TYPE>'. Maybe you forgot the '->'?")

                val (location, type) = repo.split("@")
                    .takeIf { it.size == 2 }
                    ?: throw IllegalArgumentException("Invalid extension location specified: Found '$repo' but format should be '<REPO>@<TYPE>'. Maybe you forgot the '@'?")

                descriptor to (location to type)
            }?.mapKeys {
                ExtensionArtifactRequest(
                    it.key
                )
            }?.mapValues {
                val (location, type) = it.value
                if (type == "default") ExtensionRepositorySettings.default(location, preferredHash = HashType.SHA1)
                else if (type == "local") ExtensionRepositorySettings.local(location, preferredHash = HashType.SHA1)
                else throw IllegalArgumentException("Unknown repository type: '$type' of repository at location '$location' when loading extension '${it.value}'")
            } ?: mapOf()

        val mappings = MinecraftBootstrapper.instance.minecraftHandler.minecraftReference.mappings
        val yakContext = createContext(
            context,
            mappings
        )

        graph = ExtensionGraph(
            Path.of(cache),
            Archives.Finders.JPM_FINDER,
            PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
            this::class.java.classLoader,
            context.bootContext.dependencyProviders,
            context,
            yakContext,
            mappings,
            MinecraftBootstrapper.instance.minecraftHandler.minecraftReference.archive,
        )

        val either = either.eager {
            toLoad.map { (request, settings) ->
                graph.get(request).handleErrorWith {
                    graph.cacherOf(settings).cache(request)

                    graph.get(request)
                }
            }.map { it.bind() }
        }

        val nodes = checkNotNull(either.orNull()) { "Failed to load extensions due to exception '$either'" }

        this.extensions.addAll(nodes)


        val minecraftHandler = MinecraftBootstrapper.instance.minecraftHandler

        val flatMap = this.extensions.flatMap { node ->
            if (node.runtimeModel.mixins.isNotEmpty()) checkNotNull(node.archiveReference) { "Extension has registered mixins but no archive! Please remove this mixins or add a archive." }
            node.runtimeModel.mixins.flatMap { mixin ->
                mixin.injections.map {
                    val provider = yakContext.injectionProviders[it.type]
                        ?: throw IllegalArgumentException("Unknown mixin type: '${it.type}' in mixin class: '${mixin.classname}'")

                    MixinMetadata(
                        provider.parseData(it.options, node.archiveReference!!),
                        provider.get() as MixinInjection<MixinInjection.InjectionData>
                    ) to mappings.mapClassName(mixin.destination.withSlashes()).withDots()
                }
            }

        }
        flatMap.forEach { (it, to) -> minecraftHandler.registerMixin(to, it) }

        // Init minecraft
        minecraftHandler.writeAll()
        minecraftHandler.loadMinecraft()

        nodes.forEach {

            val ref = it.extension?.process?.ref
            ref?.supplyMinecraft(minecraftHandler.archive)
            val extension = ref?.extension

            extension?.init(it.archive!!)

            extension?.init(ExtensionContext(context, yakContext))
        }

        minecraftHandler.startMinecraft()
    }

    override fun onDisable() {
        extensions.forEach {
            it.extension?.process?.ref?.extension?.cleanup()
        }
    }

    internal companion object {
        private val logger = Logger.getLogger(this::class.simpleName)

        fun createContext(context: ComponentContext, mappings: MappedArchive): YakContext {
            fun Map<String, String>.dataNotNull(name: String): String =
                checkNotNull(this[name]) { "Invalid Mixin options, no '$name' value provided in '$this'." }

            return YakContext(context, mutableListOf(
                object : MixinInjectionProvider<SourceInjectionData> {
                    override val type: String = "source"
                    private val pointCache = HashMap<String, SourceInjectionPoint>()

                    override fun parseData(
                        data: Map<String, String>,
                        ref: ArchiveReference
                    ): SourceInjectionData {
                        val self = data.dataNotNull("self")
                        val point = data.dataNotNull("point")

                        val clsSelf = ref.reader["${self.withSlashes()}.class"] ?: throw IllegalArgumentException("Failed to find class: '$self' in extension when loading source injection.")
                        val node = ClassNode().also { ClassReader(clsSelf.resource.open()).accept(it, 0) }

                        val toWithSlashes = data.dataNotNull("to").withSlashes()
                        val methodTo = data.dataNotNull("methodTo")
                        val data = SourceInjectionData(
                            mappings.mapClassName(toWithSlashes).withDots(),
                            self,
                            run {
                                val methodFrom = data.dataNotNull("methodFrom")


                                ProvidedInstructionReader(
                                    node.methods.firstOrNull {
                                        (it.name + it.desc) == methodFrom
                                    }?.instructions
                                        ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                                )
                            },
                            run {
                                mappings.mapMethodSignature(
                                    toWithSlashes,
                                    methodTo,
                                )
                            },
                            pointCache[point] ?: run {
                                loadInjectionPoint(point, ref).also { pointCache[point] = it }
                            }
                        )

                        return data
                    }

                    override fun get(): MixinInjection<SourceInjectionData> = SourceInjection
                },
                object : MixinInjectionProvider<MethodInjectionData> {
                    override val type: String = "method"

                    override fun parseData(
                        data: Map<String, String>,
                        ref: ArchiveReference
                    ): MethodInjectionData {
                        val self = data.dataNotNull("self")
                        val classSelf = ref.reader["${self.withSlashes()}.class"] ?: throw IllegalArgumentException("Failed to find class: '$self' when loading method injections.")
                        val node = ClassNode().also { ClassReader(classSelf.resource.open()).accept(it, 0) }

                        return MethodInjectionData(
                            mappings.mapClassName(data.dataNotNull("to").withSlashes()).withDots(),
                            self,
                            run {
                                val methodFrom = data.dataNotNull("methodFrom")

                                ProvidedInstructionReader(
                                    node.methods.firstOrNull {
                                        (it.name + it.desc) == methodFrom
                                    }?.instructions
                                        ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                                )
                            },

                            data.dataNotNull("access").toInt(),
                            data.dataNotNull("name"),
                            data.dataNotNull("description"),
                            data["signature"],
                            data.dataNotNull("exceptions").split(',')
                        )
                    }


                    override fun get(): MixinInjection<MethodInjectionData> = MethodInjection
                },
                object : MixinInjectionProvider<FieldInjectionData> {
                    override val type: String = "field"

                    override fun parseData(
                        data: Map<String, String>,
                        ref: ArchiveReference
                    ): FieldInjectionData {
                        return FieldInjectionData(
                            data.dataNotNull("access").toInt(),
                            data.dataNotNull("name"),
                            data.dataNotNull("type"),
                            data["signature"],
                            run {
                                if (data["value"] != null) logger.log(
                                    Level.WARNING,
                                    "Cannot set initial values in mixins at this time, this will eventually be a feature."
                                )
                                null
                            }
                        )
                    }

                    override fun get(): MixinInjection<FieldInjectionData> = FieldInjection
                }
            ).associateByTo(HashMap(), MixinInjectionProvider<*>::type),
                listOf(
                    ProGuardMappingParser
                ).associateByTo(HashMap(), MappingProvider::type)
            )
        }

        private fun loadInjectionPoint(name: String, archive: ArchiveReference): SourceInjectionPoint {
            return when (name) {
                "net.yakclient.components.yak.mixin.InjectionPoints\$AfterBegin" -> InjectionPoints.AfterBegin()
                "net.yakclient.components.yak.mixin.InjectionPoints\$BeforeEnd" -> InjectionPoints.BeforeEnd()
                "net.yakclient.components.yak.mixin.InjectionPoints\$BeforeInvoke" -> InjectionPoints.BeforeInvoke()
                "net.yakclient.components.yak.mixin.InjectionPoints\$BeforeReturn" -> InjectionPoints.BeforeReturn()
                "net.yakclient.components.yak.mixin.InjectionPoints\$Overwrite" -> InjectionPoints.Overwrite()
                else -> throw IllegalArgumentException("Invalid injection point: '$name'. Custom injections points are not yet implemented.")
            }
        }
    }
}