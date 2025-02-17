//package dev.extframework.tooling.api.extension
//
//import dev.extframework.tooling.api.environment.EnvironmentAttribute
//import dev.extframework.tooling.api.environment.EnvironmentAttributeKey
//import dev.extframework.tooling.api.environment.ExtensionEnvironment
//import dev.extframework.tooling.api.environment.getOrNull
//
//public interface ExtensionNodeObserver : EnvironmentAttribute {
//    override val key: EnvironmentAttributeKey<*>
//        get() = ExtensionNodeObserver
//    public fun observe(node: ExtensionNode)
//
//    public companion object : EnvironmentAttributeKey<ExtensionNodeObserver>
//}
//
//public fun ExtensionEnvironment.observeNodes(observer: (ExtensionNode) -> Unit) {
//    val realObserver = get(ExtensionNodeObserver).getOrNull()?.let {
//        object: ExtensionNodeObserver {
//            override fun observe(node: ExtensionNode) {
//                it.observe(node)
//                observer(node)
//            }
//        }
//    } ?: object  : ExtensionNodeObserver {
//        override fun observe(node: ExtensionNode) {
//            observer(node)
//        }
//    }
//
//    this += realObserver
//}