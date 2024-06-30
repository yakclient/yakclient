@file:JvmName("EnvironmentComposition")

package dev.extframework.components.extloader.api.environment

public fun interface EnvironmentAttributeUpdater<T : EnvironmentAttribute> : (T) -> Unit

public open class ExtLoaderEnvironment {
    private val attributes: MutableMap<EnvironmentAttributeKey<*>, EnvironmentAttribute> = HashMap()
    private val updates: MutableMap<EnvironmentAttributeKey<*>, MutableList<EnvironmentAttributeUpdater<*>>> = HashMap()

    public operator fun <T : EnvironmentAttribute> get(key: EnvironmentAttributeKey<T>): DeferredValue<T> {
        return defer {
            attributes[key] as? T
        }
    }

    public fun <T : EnvironmentAttribute> update(
        key: EnvironmentAttributeKey<T>,
        update: EnvironmentAttributeUpdater<T>
    ) {
        updates[key]?.apply {
            add(update)
        } ?: updates.put(key, mutableListOf(update))

        get(key).getOrNull()?.let(update)
    }

    public operator fun <T : EnvironmentAttribute> plusAssign(attribute: T) {
        updates[attribute.key]?.forEach {
            (it as EnvironmentAttributeUpdater<T>)(attribute)
        }

        attributes[attribute.key] = attribute
    }

    public operator fun plusAssign(other: ExtLoaderEnvironment) {
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
