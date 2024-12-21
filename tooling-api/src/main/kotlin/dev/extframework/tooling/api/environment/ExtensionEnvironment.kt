@file:JvmName("EnvironmentComposition")

package dev.extframework.tooling.api.environment

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

public fun interface EnvironmentAttributeUpdater<T : EnvironmentAttribute> : (T) -> T

public open class ExtensionEnvironment {
    private val attributes: MutableMap<EnvironmentAttributeKey<*>, EnvironmentAttribute> = ConcurrentHashMap()
    private val updates: MutableMap<EnvironmentAttributeKey<*>, ConcurrentLinkedQueue<EnvironmentAttributeUpdater<*>>> =
        ConcurrentHashMap()

    public operator fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): DeferredValue<T> {
        return defer(key.toString()) {
            val initial = attributes[key] as? T ?: return@defer null

            val updated = (updates[key] ?: listOf()).fold(initial) { acc, it ->
                (it as EnvironmentAttributeUpdater<T>).invoke(acc)
            }
            attributes[key] = updated
            updates[key]?.clear()

            updated
        }
    }

    // Performs a lazily evaluated update on the given key, the update only happens once.
    public fun <T : EnvironmentAttribute> update(
        key: EnvironmentAttributeKey<T>,
        updater: EnvironmentAttributeUpdater<T>
    ) {
        (updates[key] ?: ConcurrentLinkedQueue<EnvironmentAttributeUpdater<*>>().also { updates.put(key, it) }).apply {
            add(updater)
        }
    }

    public fun <T : EnvironmentAttribute> set(attribute: T) {
        attributes[attribute.key] = attribute
    }

    public fun <T : EnvironmentAttribute> setUnless(attribute: T) {
        if (!attributes.containsKey(attribute.key)) {
            set(attribute)
        }
    }

    public operator fun <T : EnvironmentAttribute> plusAssign(attribute: T) {
        set(attribute)
    }

    public operator fun plusAssign(other: ExtensionEnvironment) {
        other.attributes.forEach { (_, attribute) ->
            updates[attribute.key]?.forEach {
                (it as EnvironmentAttributeUpdater<EnvironmentAttribute>)(attribute)
            }
        }

        attributes.putAll(other.attributes)
    }
}

public interface EnvironmentAttribute {
    public val key: EnvironmentAttributeKey<*>
}

public interface EnvironmentAttributeKey<T : EnvironmentAttribute>
