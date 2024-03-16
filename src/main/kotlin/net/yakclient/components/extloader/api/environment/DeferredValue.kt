package net.yakclient.components.extloader.api.environment

public interface DeferredValue<T> {
    public fun get(): Result<T>

    public fun listen(listener: (T) -> Unit)
}

public inline fun <T> defer(crossinline provider: () -> T?): DeferredValue<T> {
    return object : DeferredValue<T> {
        private var value: T? = null
        private val listeners = ArrayList<(T) -> Unit>()

        override fun get(): Result<T> {
            if (value == null) {
                value = provider()
            }

            if (value != null) {
                val nonNullValue = value!!
                listeners.removeAll {
                    it(nonNullValue)
                    true
                }

                return Result.success(nonNullValue)
            }

            return Result.failure(UninitializedPropertyAccessException())
        }

        override fun listen(listener: (T) -> Unit) {
            if (value != null) listener(value!!)
            else listeners.add(listener)
        }
    }
}

public inline fun <T, V> DeferredValue<T>.map(crossinline transformer: (T) -> V): DeferredValue<V> {
    return defer {
        get().map(transformer).getOrNull()
    }
}

public fun <T> DeferredValue<T>.getOrNull(): T? = get().getOrNull()

public fun <T> DeferredValue<T>.extract(): T = get().getOrThrow()