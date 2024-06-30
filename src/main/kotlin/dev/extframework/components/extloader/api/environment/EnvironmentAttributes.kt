package dev.extframework.components.extloader.api.environment

import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.archives.mixin.SourceInjectionPoint
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.components.extloader.api.extension.partition.ExtensionPartitionLoader
import dev.extframework.components.extloader.api.mixin.MixinInjectionProvider
import dev.extframework.components.extloader.api.exception.ExceptionContextSerializer
import dev.extframework.`object`.MutableObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import java.nio.file.Path

public val dependencyTypesAttrKey: DependencyTypeContainerAttribute.Key =
    DependencyTypeContainerAttribute.Key
public val mappingProvidersAttrKey: MutableObjectSetAttribute.Key<MappingsProvider> =
    MutableObjectSetAttribute.Key("mapping-providers")
public val mixinTypesAttrKey: MutableObjectContainerAttribute.Key<MixinInjectionProvider<*,*>> =
    MutableObjectContainerAttribute.Key("mixin-types")
public val injectionPointsAttrKey: MutableObjectContainerAttribute.Key<SourceInjectionPoint> =
    MutableObjectContainerAttribute.Key("injection-points")
public val partitionLoadersAttrKey : MutableObjectContainerAttribute.Key<ExtensionPartitionLoader<*>> =
    MutableObjectContainerAttribute.Key("partition-loader")
public val exceptionContextSerializers : MutableObjectSetAttribute.Key<ExceptionContextSerializer<*>>
    = MutableObjectSetAttribute.Key("exception-context-serializer")

public val ExtLoaderEnvironment.archiveGraph: ArchiveGraph
    get() = get(ArchiveGraphAttribute).getOrNull()!!.graph

public data class ArchiveGraphAttribute(
    val graph: ArchiveGraph
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ArchiveGraphAttribute

    public companion object : EnvironmentAttributeKey<ArchiveGraphAttribute>
}

public class DependencyTypeContainerAttribute(
    public val container: DependencyTypeContainer
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = Key

    public companion object Key : EnvironmentAttributeKey<DependencyTypeContainerAttribute>
}

public open class MutableObjectContainerAttribute<T>(
    override val key: EnvironmentAttributeKey<*>,
    public open val container: MutableObjectContainer<T> = ObjectContainerImpl()
) : EnvironmentAttribute {
    public constructor(name: String, delegate: MutableObjectContainer<T> = ObjectContainerImpl()) : this(
        Key<T>(name),
        delegate
    )

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

public data class ApplicationMappingTarget(
    public val namespace: String
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ApplicationMappingTarget

    public companion object : EnvironmentAttributeKey<ApplicationMappingTarget>
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