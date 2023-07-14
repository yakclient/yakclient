package net.yakclient.components.extloader.mixin

public interface InjectionType {
    public val type: String

    public fun getInjection(options: Map<String, String>)
}