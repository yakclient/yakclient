package net.yakclient.components.extloader.mixin

import kotlin.reflect.KClass

public class MixinInjectionsHandler {
    internal val mixinClasses = ArrayList<KClass<*>>()

    public fun registerMixin(cls: KClass<*>) {
        mixinClasses.add(cls)
    }

    public fun registerMixin(cls: Class<*>) {
        mixinClasses.add(cls.kotlin)
    }
}