@file:JvmName("Environments")

package dev.extframework.extloader.environment

import dev.extframework.archives.mixin.SourceInjectionPoint
import dev.extframework.archives.mixin.SourceInjectors
import dev.extframework.extloader.exception.*
import dev.extframework.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.exception.ExceptionContextSerializer
import dev.extframework.internal.api.extension.ExtensionClassLoaderProvider
import dev.extframework.internal.api.extension.partition.ExtensionPartitionLoader
import java.nio.file.Path


internal fun MutableObjectContainerAttribute<SourceInjectionPoint>.registerMixinPoints() {
    container.register("after-begin", SourceInjectors.AFTER_BEGIN)
    container.register("before-end", SourceInjectors.BEFORE_END)
    container.register("before-invoke", SourceInjectors.BEFORE_INVOKE)
    container.register("before-return", SourceInjectors.BEFORE_RETURN)
    container.register("overwrite", SourceInjectors.OVERWRITE)
}

internal fun MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>.registerLoaders() {
//    MainPartitionLoader().also { container.register(it.type, it) }
    TweakerPartitionLoader().also { container.register(it.type, it) }
//    VersionedPartitionLoader().also { container.register(it.type, it) }
//    FeaturePartitionLoader().also { container.register(it.type, it) }
}

internal fun MutableObjectSetAttribute<ExceptionContextSerializer<*>>.registerBasicSerializers(): MutableObjectSetAttribute<ExceptionContextSerializer<*>> {
    AnyContextSerializer().also(::add)
    IterableContextSerializer().also(::add)
    MapContextSerializer().also(::add)
    StringContextSerializer().also(::add)
    PathContextSerializer().also(::add)

    return this
}

internal fun CommonEnvironment(
    path: Path
): ExtensionEnvironment {
    val env = ExtensionEnvironment()

//    env += MutableObjectSetAttribute<MappingsProvider>(mappingProvidersAttrKey).also {
//        it.add(
//            MojangExtensionMappingProvider(
//                path resolve "mappings" resolve "mojang"
//            )
//        )
//    }
//    env += MutableObjectContainerAttribute<MixinInjectionProvider<*, *>>(mixinTypesAttrKey)
//    env += MutableObjectContainerAttribute<SourceInjectionPoint>(injectionPointsAttrKey)
    env += object : ExtensionClassLoaderProvider {}
//    env += object : ApplicationParentClProvider {
//        override fun getParent(linker: TargetLinker, environment: ExtensionEnvironment): ClassLoader {
//            return IntegratedLoader(
//                name = "${linker.target.node.descriptor} Extension access provider",
//                classProvider = ArchiveClassProvider(
//                    (linker.extensionTarget.relationship.node
//                            as ClassLoadedArchiveNode<*>).handle
//                ),
//                resourceProvider = ArchiveResourceProvider(
//                    (linker.extensionTarget.relationship.node
//                            as ClassLoadedArchiveNode<*>).handle
//                ),
//                parent = environment[ParentClassloaderAttribute].getOrNull()!!.cl
//            )
//        }
//    }

    env += MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>(partitionLoadersAttrKey)

//    env[mixinTypesAttrKey].extract().registerMixins()
//    env[injectionPointsAttrKeyAttrKey].extract().registerMixinPoints()
    env[partitionLoadersAttrKey].extract().registerLoaders()

    return env
}

//internal fun ProdEnvironment(
//    path: Path
//): ExtLoaderEnvironment {
//    val env = ExtLoaderEnvironment()
//
//    env += MutableObjectSetAttribute<MappingsProvider>(mappingProvidersAttrKey).also {
//        it.add(
//            MojangExtensionMappingProvider(
//                path resolve "mappings" resolve "mojang"
//            )
//        )
//    }
//    env += MutableObjectContainerAttribute<MixinInjectionProvider<*, *>>(mixinTypesAttrKey)
//    env += MutableObjectContainerAttribute<SourceInjectionPoint>(injectionPointsAttrKey)
//    env += object : ExtensionRunner {
//        override fun init(node: ExtensionNode) {
//            try {
//                node.partitions
//                    .map { it.node }
//                    .filterIsInstance<MainPartitionNode>()
//                    .firstOrNull()?.extension?.init()
//            } catch (e: Throwable) {
//                throw StructuredException(
//                    ExtLoaderExceptions.ExtensionInitializationException,
//                    cause = e,
//                    message = "An exception occurred while initializing: '${node.erm.descriptor}'"
//                )
//            }
//        }
//    }
//    env += object : ExtensionClassLoaderProvider {}
//    env += object : ApplicationParentClProvider {
//        override fun getParent(linker: TargetLinker, environment: ExtLoaderEnvironment): ClassLoader {
//            return IntegratedLoader(
//                name = "${linker.targetName} Misc access provider",
//                classProvider = linker.miscTarget.relationship.classes,
//                resourceProvider = linker.miscTarget.relationship.resources,
//                parent = environment[ParentClassloaderAttribute].getOrNull()!!.cl
//            )
//        }
//    }
//
//    env += MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>(partitionLoadersAttrKey)
//
//    env[mixinTypesAttrKey].extract().registerMixins()
//    env[injectionPointsAttrKey].extract().registerMixinPoints()
//    env[partitionLoadersAttrKey].extract().registerLoaders()
//
//    return env
//}