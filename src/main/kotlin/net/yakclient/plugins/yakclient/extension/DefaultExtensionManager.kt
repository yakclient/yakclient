package net.yakclient.plugins.yakclient.extension
//
//import net.yakclient.plugins.yakclient.AppInstance
//import net.yakclient.plugins.yakclient.mixin.InjectionMetadata
//import net.yakclient.plugins.yakclient.mixin.MixinRegistry
//
//public class DefaultExtensionManager<T : ExtensionInfo>(
//    loader: ExtensionLoader<T>,
//    mixinRegistry: MixinRegistry, app: AppInstance
//) : ExtensionManager<T>(
//    loader,
//    mixinRegistry,
//    app
//) {
//    override fun transformInjection(key: ExtKey, metadata: InjectionMetadata): InjectionMetadata {
//        return metadata
//    }
//
//    override fun generateKey(ext: Extension): ExtKey {
//        return object : ExtKey {
//            override fun equals(other: Any?): Boolean = System.identityHashCode(other) == hashCode()
//
//            override fun hashCode(): Int = System.identityHashCode(ext)
//        }
//    }
//}