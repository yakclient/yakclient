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
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import net.yakclient.components.extloader.mixin.FieldInjectionProvider
import net.yakclient.components.extloader.mixin.MethodInjectionProvider
import net.yakclient.components.extloader.mixin.SourceInjectionProvider
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import net.yakclient.components.extloader.api.target.ApplicationParentClProvider
import net.yakclient.components.extloader.target.TargetLinker
import java.nio.file.Path

//public class EnvironmentBuilder(
//    internal val attributes: MutableMap<EnvironmentAttributeKey<*>, EnvironmentAttribute>
//) : ExtLoaderEnvironment {
//    override fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): T? = attributes[key] as T?
//    override fun plus(other: ExtLoaderEnvironment): ExtLoaderEnvironment {
//        check(other is EnvironmentBuilder) {"Cannot add these environments together, they are not the same type"}
//        return EnvironmentBuilder((other.attributes + attributes).toMutableMap())
//    }
//
//    internal fun <T : EnvironmentAttribute> T.add(): T {
//        attributes[this.key] =
//            this
//
//        return this
//    }
//
//    internal fun <T: EnvironmentAttribute> T.add(key: EnvironmentAttributeKey<T>) : T {
//        attributes[key] = this
//        return this
//    }
//}

//public class EnvironmentBuilder(
//) : ExtLoaderEnvironment {
//    override fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): T? = attributes[key] as T?
//    override fun plus(other: ExtLoaderEnvironment): ExtLoaderEnvironment {
//        check(other is EnvironmentBuilder) {"Cannot add these environments together, they are not the same type"}
//        return EnvironmentBuilder((other.attributes + attributes).toMutableMap())
//    }
//
//    internal fun <T : EnvironmentAttribute> T.add(): T {
//        attributes[this.key] =
//            this
//
//        return this
//    }
//
//    internal fun <T: EnvironmentAttribute> T.add(key: EnvironmentAttributeKey<T>) : T {
//        attributes[key] = this
//        return this
//    }
//}
//internal class EnvironmentFactory<T>(
//    private val name: String,
//    private val builderAction: EnvironmentBuilder.(T) -> Unit,
//    private val parseAction: (ContextNodeValue) -> T
//) {
//    fun build(contextNodeValue: ContextNodeValue): ExtLoaderEnvironment {
//        return EnvironmentBuilder(name, HashMap()).also { it.builderAction(parseAction(contextNodeValue)) }
//    }
//}

//public fun createEnvironment(
//    builderAction: EnvironmentBuilder.() -> Unit,
//): ExtLoaderEnvironment = EnvironmentBuilder(HashMap()).apply(builderAction)


//internal fun registerBasicProviders(
//    container: MutableObjectContainer<MixinInjectionProvider<*>>
//) {
//    listOf(
//        SourceInjectionProvider(),
//        MethodInjectionProvider(),
//        FieldInjectionProvider(),
//    ).forEach { container.register(it.type, it) }
//}
//
//private data class ExtDevConfiguration(
//    val mappingsType: String,
//    val mappingsVersion: String,
//    val extDescriptor: ExtensionDescriptor,
//    val extRepository: ExtensionRepositorySettings,
//)

// This is the current issue as i understand it. I want everything to be extensible, one of the things
// that extension makers should be able to change, is the mapping type. When doing extensions like this,
// there is an issue. Minecraft NEEDS to be mapped before ANY extension is loaded (unless we get fancy
// with lazy class loaders which just sounds awful), if a extension uses another mapping type and expects
// one of its child extensions to register the mapping, that wont work (because to load the child, mc needs
// to already be mapped. There is another issue, which is that if we run unmapped minecraft, child extensions
// that use another minecraft mapping will need to first be mapped to vanilla obfuscated, then back to what the
// extension that is being tested is using.) One possible solution to this, is to enforce the "main partition
// does not rely on minecraft rule" and load main partitions before other ones, have them do some logic, and
// then load other partitions. This allows us to load in 2 stages. Im not sure though, its late, so im gonna
// go to bed. All in all, i think this idea of running mapped minecraft (de-obfuscated) is just kindof bad,
// there must be some other way to get all this good debugging functionallity??

internal fun MutableObjectContainerAttribute<MixinInjectionProvider<*,*>>.registerMixins() {
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
    env += MutableObjectContainerAttribute<MixinInjectionProvider<*,*>>(mixinTypesAttrKey)
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

    env[mixinTypesAttrKey].getOrNull()?.registerMixins()
    env[injectionPointsAttrKey].getOrNull()?.registerMixinPoints()

    return env
}

//    CoroutineContext
//    val extensionGraph = ExtensionGraph(
//        path resolve "extensions",
//        Archives.Finders.ZIP_FINDER,
//        PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
//        parentCl,
//        dependencyTypeContainer,
//        this
//    )
//    val app = createMinecraftApp(bootstrapper.minecraftHandler, bootstrapper::end).add()
//    transformArchive(
//        app.reference.reference,
//        app.reference.dependencies,
//
//    )
//}