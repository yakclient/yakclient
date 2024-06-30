@file:JvmName("Environments")

package dev.extframework.components.extloader.environment

import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.archives.mixin.SourceInjectionPoint
import dev.extframework.archives.mixin.SourceInjectors
import dev.extframework.boot.loader.IntegratedLoader
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.api.environment.*
import dev.extframework.components.extloader.api.exception.ExceptionContextSerializer
import dev.extframework.components.extloader.api.exception.StructuredException
import dev.extframework.components.extloader.api.extension.ExtensionClassLoaderProvider
import dev.extframework.components.extloader.api.extension.ExtensionRunner
import dev.extframework.components.extloader.api.extension.descriptor
import dev.extframework.components.extloader.api.extension.partition.ExtensionPartitionLoader
import dev.extframework.components.extloader.api.mixin.MixinInjectionProvider
import dev.extframework.components.extloader.api.target.ApplicationParentClProvider
import dev.extframework.components.extloader.exception.*
import dev.extframework.components.extloader.exception.AnyContextSerializer
import dev.extframework.components.extloader.exception.IterableContextSerializer
import dev.extframework.components.extloader.exception.MapContextSerializer
import dev.extframework.components.extloader.exception.StringContextSerializer
import dev.extframework.components.extloader.extension.ExtensionNode
import dev.extframework.components.extloader.extension.mapping.MojangExtensionMappingProvider
import dev.extframework.components.extloader.extension.partition.*
import dev.extframework.components.extloader.mixin.FieldInjectionProvider
import dev.extframework.components.extloader.mixin.MethodInjectionProvider
import dev.extframework.components.extloader.mixin.SourceInjectionProvider
import dev.extframework.components.extloader.target.TargetLinker
import java.nio.file.Path

internal fun MutableObjectContainerAttribute<MixinInjectionProvider<*, *>>.registerMixins() {
    container.register("source", SourceInjectionProvider())
    container.register("method", MethodInjectionProvider())
    container.register("field", FieldInjectionProvider())
}

internal fun MutableObjectContainerAttribute<SourceInjectionPoint>.registerMixinPoints() {
    container.register("after-begin", SourceInjectors.AFTER_BEGIN)
    container.register("before-end", SourceInjectors.BEFORE_END)
    container.register("before-invoke", SourceInjectors.BEFORE_INVOKE)
    container.register("before-return", SourceInjectors.BEFORE_RETURN)
    container.register("overwrite", SourceInjectors.OVERWRITE)
}

internal fun MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>.registerLoaders() {
    MainPartitionLoader().also { container.register(it.type, it) }
    TweakerPartitionLoader().also { container.register(it.type, it) }
    VersionedPartitionLoader().also { container.register(it.type, it) }
    FeaturePartitionLoader().also { container.register(it.type, it) }
}

internal fun MutableObjectSetAttribute<ExceptionContextSerializer<*>>.registerBasicSerializers(): MutableObjectSetAttribute<ExceptionContextSerializer<*>> {
    AnyContextSerializer().also(::add)
    IterableContextSerializer().also(::add)
    MapContextSerializer().also(::add)
    StringContextSerializer().also(::add)
    PathContextSerializer().also(::add)

    return this
}

internal fun ExtensionDevEnvironment(
    path: Path
): ExtLoaderEnvironment {
    val env = ExtLoaderEnvironment()

    env += MutableObjectSetAttribute<MappingsProvider>(mappingProvidersAttrKey).also {
        it.add(
            MojangExtensionMappingProvider(
                path resolve "mappings" resolve "mojang"
            )
        )
    }
    env += MutableObjectContainerAttribute<MixinInjectionProvider<*, *>>(mixinTypesAttrKey)
    env += MutableObjectContainerAttribute<SourceInjectionPoint>(injectionPointsAttrKey)
    env += object : ExtensionRunner {
        override fun init(node: ExtensionNode) {
            try {
                node.partitions
                    .map { it.node }
                    .filterIsInstance<MainPartitionNode>()
                    .firstOrNull()?.extension?.init()
            } catch (e: Throwable) {
                throw StructuredException(
                    ExtLoaderExceptions.ExtensionInitializationException,
                    cause = e,
                    message = "An exception occurred while initiating: '${node.erm.descriptor}'"
                )
            }
        }
    }
    env += object : ExtensionClassLoaderProvider {}
    env += object : ApplicationParentClProvider {
        override fun getParent(linker: TargetLinker, environment: ExtLoaderEnvironment): ClassLoader {
            return IntegratedLoader(
                name = "${linker.targetName} Misc access provider",
                classProvider = linker.miscTarget.relationship.classes,
                resourceProvider = linker.miscTarget.relationship.resources,
                parent = environment[ParentClassloaderAttribute].getOrNull()!!.cl
            )
        }
    }

    env += MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>(partitionLoadersAttrKey)

    env[mixinTypesAttrKey].extract().registerMixins()
    env[injectionPointsAttrKey].extract().registerMixinPoints()
    env[partitionLoadersAttrKey].extract().registerLoaders()

    return env
}