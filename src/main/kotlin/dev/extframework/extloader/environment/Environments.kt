@file:JvmName("Environments")

package dev.extframework.extloader.environment

import dev.extframework.extloader.exception.*
import dev.extframework.extloader.extension.partition.TweakerPartitionLoader
import dev.extframework.tooling.api.environment.*
import dev.extframework.tooling.api.exception.ExceptionContextSerializer
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionLoader

internal fun MutableObjectContainerAttribute<ExtensionPartitionLoader<*>>.registerLoaders() {
    TweakerPartitionLoader().also { container.register(it.type, it) }
}

internal fun MutableObjectSetAttribute<ExceptionContextSerializer<*>>.registerBasicSerializers(): MutableObjectSetAttribute<ExceptionContextSerializer<*>> {
    AnyContextSerializer().also(::add)
    IterableContextSerializer().also(::add)
    MapContextSerializer().also(::add)
    StringContextSerializer().also(::add)
    PathContextSerializer().also(::add)

    return this
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