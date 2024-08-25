package dev.extframework.internal.api.environment

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.internal.api.exception.ExceptionContextSerializer
import dev.extframework.internal.api.extension.partition.ExtensionPartitionLoader
import dev.extframework.`object`.MutableObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import java.nio.file.Path

public val dependencyTypesAttrKey: DependencyTypeContainerAttribute.Key =
    DependencyTypeContainerAttribute.Key
public val partitionLoadersAttrKey : MutableObjectContainerAttribute.Key<ExtensionPartitionLoader<*>> =
    MutableObjectContainerAttribute.Key("partition-loader")
public val exceptionCxtSerializersAttrKey : MutableObjectSetAttribute.Key<ExceptionContextSerializer<*>>
    = MutableObjectSetAttribute.Key("exception-context-serializer")
public val wrkDirAttrKey: ValueAttribute.Key<Path> = ValueAttribute.Key("working-directory")
public val parentCLAttrKey: ValueAttribute.Key<ClassLoader> = ValueAttribute.Key("parent-classloader")

public val ExtensionEnvironment.archiveGraph: ArchiveGraph
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
    override val key: Key<T>,
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

public open class MutableObjectSetAttribute<T>(
    override val key: Key<T>,
) : EnvironmentAttribute, ArrayList<T>() {
    public data class Key<T>(
        val name: String
    ) : EnvironmentAttributeKey<MutableObjectSetAttribute<T>>
}

public open class ValueAttribute<T>(
    public val value: T,
    override val key: Key<T>
) : EnvironmentAttribute {
    public data class Key<T>(
        val name: String
    ) : EnvironmentAttributeKey<ValueAttribute<T>>
}
