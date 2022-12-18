package net.yakclient.components.yak

import arrow.core.continuations.either
import arrow.core.handleErrorWith
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import net.yakclient.archive.mapper.MappedArchive
import net.yakclient.archives.Archives
import net.yakclient.archives.extension.parameters
import net.yakclient.archives.mixin.*
import net.yakclient.archives.transform.MethodSignature
import net.yakclient.archives.transform.Sources
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.component.SoftwareComponent
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.common.util.runCatching
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
            Archives.Resolvers.JPM_RESOLVER,
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
            if (node.runtimeModel.mixins.isNotEmpty()) checkNotNull(node.archive) { "Extension has registered mixins but no archive! Please remove this mixins or add a archive." }
            node.runtimeModel.mixins.flatMap { mixin ->
                mixin.injections.map {
                    val provider = yakContext.injectionProviders[it.type]
                        ?: throw IllegalArgumentException("Unknown mixin type: '${it.type}' in mixin class: '${mixin.classname}'")

                    MixinMetadata(
                        provider.parseData(it.options, node.archive!!.classloader),
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
            val extension = it.extension?.process?.extension
            extension?.init(it.archive!!)

            extension?.init(ExtensionContext(context, yakContext))
        }

        minecraftHandler.startMinecraft()
    }

    override fun onDisable() {
        extensions.forEach {
            it.extension?.process?.extension?.cleanup()
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
                        classloader: ClassLoader
                    ): SourceInjectionData {
                        val classSelf = data.dataNotNull("self")
                        val point = data.dataNotNull("point")

                        val clsFrom = classloader.loadClass(classSelf)

                        val toWithSlashes = data.dataNotNull("to").withSlashes()
                        val methodTo = data.dataNotNull("methodTo")
                        val data = SourceInjectionData(
                            mappings.mapClassName(toWithSlashes).withDots(),
                            classSelf,
                            run {
                                val methodFrom = data.dataNotNull("methodFrom")
                                val sig = MethodSignature.of(methodFrom)
                                val parameters = parameters(sig.desc).toSet()

                                Sources.of(
                                    clsFrom.methods.find { method ->
                                        method.name == sig.name && method.parameters.all { parameters.contains(it.name) }
                                    } ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
                                )
                            },
                            run {
                                mappings.mapMethodSignature(
                                    toWithSlashes,
//                                    toWithSlashes,
                                    methodTo,
//                                    mappings.mapDesc(methodTo)
                                )
                            },
                            pointCache[point] ?: run {
                                val pointClass = classloader.loadClass(point)

                                val constructor =
                                    runCatching(NoSuchMethodException::class) { pointClass.getConstructor() }
                                        ?: throw IllegalStateException("Class '$point' does not have a no-arg empty constructor! Cannot instantiate it")

                                if (!constructor.trySetAccessible()) throw IllegalStateException("Cannot access no-arg constructor in mixin class: '${pointClass.name}'")

                                constructor.newInstance() as SourceInjectionPoint
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
                        classloader: ClassLoader
                    ): MethodInjectionData {
                        val self = data.dataNotNull("self")
                        val classSelf = classloader.loadClass(self)

                        return MethodInjectionData(
                            mappings.mapClassName(data.dataNotNull("to").withSlashes()).withDots(),
                            self,
                            run {
                                val methodFrom = data.dataNotNull("methodFrom")
                                val sig = MethodSignature.of(methodFrom)
                                val parameters = parameters(sig.desc).toSet()

                                Sources.of(
                                    classSelf.methods.find { method ->
                                        method.name == sig.name && method.parameters.all { parameters.contains(it.name) }
                                    } ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
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
                        classloader: ClassLoader
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
    }
}