@file:JvmName("EnvironmentComposition")

package net.yakclient.components.extloader.api.environment

//private interface IterableEnvironment : ExtLoaderEnvironment {
//    fun forEach(consumer: (ExtLoaderEnvironment) -> Unit)
//}
//
//private data class CombinedEnvironment(
//    private val left: ExtLoaderEnvironment,
//    private val right: ExtLoaderEnvironment
//) : IterableEnvironment {
//    override fun forEach(consumer: (ExtLoaderEnvironment) -> Unit) {
//        consumer(right)
//        consumer(left)
//    }
//
//    override fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): T? {
//        return left[key] ?: right[key]
//    }
//}

//private data class AssociatedEnvironment(
//    private val map: Map<EnvironmentAttributeKey<*>, EnvironmentAttribute>
//) : IterableEnvironment {
//    override fun forEach(consumer: (ExtLoaderEnvironment) -> Unit) {
//        map.values.forEach(consumer)
//    }
//
//    override fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): T? {
//        return map[key]?.get(key)
//    }
//}

public open class ExtLoaderEnvironment {
    private val attributes: MutableMap<EnvironmentAttributeKey<*>, EnvironmentAttribute> = HashMap()
    public operator fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): T? {
        return attributes[key] as? T
    }

    public operator fun <T: EnvironmentAttribute> plusAssign(attribute: T) {
        attributes[attribute.key] = attribute
    }

    public operator fun plusAssign(other: ExtLoaderEnvironment) {
        attributes.putAll(other.attributes)
    }


    // O(n) optimizing for future runtimes which allows get to be O(1) always.
//    public operator fun plus(other: ExtLoaderEnvironment): ExtLoaderEnvironment {
//        if (this !is IterableEnvironment && other !is IterableEnvironment) return CombinedEnvironment(this, other)
//
//        val attributes = HashMap<EnvironmentAttributeKey<*>, EnvironmentAttribute>()
//
//        fun iterAdd(env: ExtLoaderEnvironment) {
//            if (env is EnvironmentAttribute) {
//                attributes[env.key] = env
//            }
//            if (env is IterableEnvironment) {
//                env.forEach(::iterAdd)
//            }
//        }
//
//        iterAdd(CombinedEnvironment(this, other))
//
//        return AssociatedEnvironment(attributes)
//    }
}

public interface EnvironmentAttribute {
    public val key: EnvironmentAttributeKey<*>

//    @Suppress("unchecked_cast")
//    override fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): T? =
//        if (this.key == key) this as T else null
}

public interface EnvironmentAttributeKey<T : EnvironmentAttribute>


// Internal API use only
//private class BasicExtLoaderEnvironment(
//    override val name: String,
//    private val attributes: Map<EnvironmentAttributeKey<*>, EnvironmentAttribute>
//) : ExtLoaderEnvironment {
//    override fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): T? = attributes[key] as T?
//}

