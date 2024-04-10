@file:JvmName("Environments")

package net.yakclient.components.extloader.environment

import net.yakclient.archive.mapper.MappingsProvider
import net.yakclient.archives.mixin.SourceInjectionPoint
import net.yakclient.archives.mixin.SourceInjectors
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionClassLoaderProvider
import net.yakclient.components.extloader.extension.ExtensionNode
import net.yakclient.components.extloader.api.extension.ExtensionRunner
import net.yakclient.components.extloader.api.extension.partition.ExtensionPartitionLoader
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import net.yakclient.components.extloader.mixin.FieldInjectionProvider
import net.yakclient.components.extloader.mixin.MethodInjectionProvider
import net.yakclient.components.extloader.mixin.SourceInjectionProvider
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import net.yakclient.components.extloader.api.target.ApplicationParentClProvider
import net.yakclient.components.extloader.extension.partition.*
import net.yakclient.components.extloader.target.TargetLinker
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

internal fun ExtensionDevEnvironment(
    path: Path
): ExtLoaderEnvironment {
    val env = ExtLoaderEnvironment()

    env += MutableObjectSetAttribute<MappingsProvider>(mappingProvidersAttrKey).also {
        it.add(
            MojangExtensionMappingProvider(
                path resolve "mapping" resolve "mojang"
            )
        )
    }
    env += MutableObjectContainerAttribute<MixinInjectionProvider<*, *>>(mixinTypesAttrKey)
    env += MutableObjectContainerAttribute<SourceInjectionPoint>(injectionPointsAttrKey)
    env += object : ExtensionRunner {
        override fun init(node: ExtensionNode) {
            node.container?.extension?.init()
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