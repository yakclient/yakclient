package net.yakclient.components.extloader.api.mapping


public sealed class MappingSpecificEntryContext {
    public abstract val type: MappingSpecificEntryType
}

public data class MixinEntryContext(
        val destination: String
) : MappingSpecificEntryContext() {
    override val type: MappingSpecificEntryType = MappingSpecificEntryType.MIXIN
}

public class ClassEntryContext : MappingSpecificEntryContext() {
    override val type: MappingSpecificEntryType = MappingSpecificEntryType.MIXIN
}

public enum class MappingSpecificEntryType {
    CLASS,
    MIXIN,
}