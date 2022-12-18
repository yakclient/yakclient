package net.yakclient.components.yak.mixin

public interface InjectionType {
    public val type: String

    public fun getInjection(options: Map<String, String>)
}