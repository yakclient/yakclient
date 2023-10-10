package net.yakclient.components.extloader.api.environment

import net.yakclient.archives.mixin.SourceInjectionPoint
import net.yakclient.boot.dependency.DependencyGraphProvider
import net.yakclient.components.extloader.api.mapping.MappingsProvider
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import net.yakclient.`object`.MutableObjectContainer
import net.yakclient.`object`.ObjectContainerImpl
import java.nio.file.Path

public val dependencyTypesAttrKey: MutableObjectContainerAttribute.Key<DependencyGraphProvider<*, *, *>> =
    MutableObjectContainerAttribute.Key("dependency-types")
public val mappingProvidersAttrKey: MutableObjectSetAttribute.Key<MappingsProvider> =
    MutableObjectSetAttribute.Key("mapping-providers")
public val mixinTypesAttrKey: MutableObjectContainerAttribute.Key<MixinInjectionProvider<*>> =
    MutableObjectContainerAttribute.Key("mixin-types")
public val injectionPointsAttrKey: MutableObjectContainerAttribute.Key<SourceInjectionPoint> =
    MutableObjectContainerAttribute.Key("injection-points")


public class MutableObjectContainerAttribute<T>(
    override val key: EnvironmentAttributeKey<*>,
    delegate: MutableObjectContainer<T> = ObjectContainerImpl()
) : EnvironmentAttribute, MutableObjectContainer<T> by delegate {
    public constructor(name: String, delegate: MutableObjectContainer<T> = ObjectContainerImpl()) : this(Key<T>(name), delegate)

    public data class Key<T>(
        val name: String
    ) : EnvironmentAttributeKey<MutableObjectContainerAttribute<T>>
}

public class MutableObjectSetAttribute<T>(
    override val key: EnvironmentAttributeKey<*>,
) : EnvironmentAttribute, ArrayList<T>() {
    public data class Key<T>(
        val name: String
    ) : EnvironmentAttributeKey<MutableObjectSetAttribute<T>>
}

public data class ApplicationMappingType(
    public val type: String
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ApplicationMappingType

    public companion object : EnvironmentAttributeKey<ApplicationMappingType>
}

public data class WorkingDirectoryAttribute(
    public val path: Path
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = WorkingDirectoryAttribute

    public companion object : EnvironmentAttributeKey<WorkingDirectoryAttribute>
}

public class ParentClassloaderAttribute(
    public val cl: ClassLoader
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ParentClassloaderAttribute

    public companion object : EnvironmentAttributeKey<ParentClassloaderAttribute>
}