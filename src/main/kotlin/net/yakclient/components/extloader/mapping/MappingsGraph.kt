package net.yakclient.components.extloader.mapping

import net.yakclient.archive.mapper.*
import net.yakclient.components.extloader.api.mapping.MappingsProvider
import java.lang.IllegalStateException
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet


public interface MappingsGraph {
    public data class ProviderEdge(
        val from: String, val to: String, val provider: MappingsProvider
    )

    public fun connectingEdges(type: String): List<ProviderEdge>
}

public fun newMappingsGraph(list: List<MappingsProvider>): MappingsGraph = object : MappingsGraph {
    // ChatGPT
    private fun generateNamespaceEdges(providers: List<MappingsProvider>): Map<String, List<MappingsGraph.ProviderEdge>> {
        return providers.flatMap { provider ->
            val namespaces = provider.namespaces.toList()
            namespaces.flatMap { from ->
                namespaces.filter { to -> to != from }
                    .map { to -> MappingsGraph.ProviderEdge(from, to, provider) }
            }
        }.groupBy { it.from }
    }

    val outEdges: Map<String, List<MappingsGraph.ProviderEdge>> = generateNamespaceEdges(list)
//        .flatMap {
//
//        }
//        .map { MappingsGraph.ProviderEdge(MappingDirection.TO_FAKE, it) }
//        .groupBy { it.provider.realType } combine list
//
//        .map { MappingsGraph.ProviderEdge(MappingDirection.TO_REAL, it) }
//        .groupBy { it.provider.fakeType }

    override fun connectingEdges(type: String): List<MappingsGraph.ProviderEdge> {
        return outEdges[type] ?: listOf()
    }
}

// BFS
public fun MappingsGraph.findShortest(
    typeFrom: String,
    typeTo: String,
): MappingsProvider {
//    fun MappingsGraph.ProviderEdge.to(): String =
//        when (direction) {
//            MappingDirection.TO_REAL -> provider.realType
//            MappingDirection.TO_FAKE -> provider.fakeType
//        }
//
//    fun MappingsGraph.ProviderEdge.from(): String =
//        when (direction) {
//            MappingDirection.TO_REAL -> provider.fakeType
//            MappingDirection.TO_FAKE -> provider.realType
//        }

    if (typeFrom == typeTo) return object : MappingsProvider {
        override val namespaces: Set<String> = setOf()
//        override val realType: String = typeFrom
//        override val fakeType: String = typeTo

        override fun forIdentifier(identifier: String): ArchiveMapping = ArchiveMapping(
            setOf(),
            MappingValueContainerImpl(mapOf()),
            MappingNodeContainerImpl(setOf()),
//            HashMap()
        )
    }

    val perimeter: Queue<String> = LinkedList()
    val edgeTo = HashMap<String, MappingsGraph.ProviderEdge>()
    val distTo = HashMap<String, Int>()
    val visited = HashSet<String>()

    perimeter.add(typeFrom)
    distTo[typeFrom] = 0

    while (!perimeter.isEmpty()) {
        val current = perimeter.remove()
        if (!visited.add(current)) continue

        if (current == typeTo) return createProviderFrom(edgeTo, typeTo, typeFrom)

        for (currentEdge in connectingEdges(current)) {
            perimeter.add(currentEdge.to)

            val currentDist = distTo[currentEdge.to] ?: Int.MAX_VALUE
            val newDist = distTo[currentEdge.from]!! + 1

            if (newDist < currentDist) {
                distTo[currentEdge.to] = newDist
                edgeTo[currentEdge.to] = currentEdge
            }
        }

        visited.add(current)
    }

    throw IllegalStateException("Failed to find path between mappings")
}

private fun createProviderFrom(
    edges: Map<String, MappingsGraph.ProviderEdge>, typeTo: String, typeFrom: String
): MappingsProvider {
//    fun MappingsGraph.ProviderEdge.from(): String =
//        when (direction) {
//            MappingDirection.TO_REAL -> provider.fakeType
//            MappingDirection.TO_FAKE -> provider.realType
//        }

    fun createPath(vertex: String): List<MappingsGraph.ProviderEdge> {
        val edge = edges[vertex] ?: return listOf()
        return createPath(edge.from) + edge
    }

    val edgePath = createPath(typeTo)
    return object : MappingsProvider {
        override val namespaces: Set<String> = setOf(typeFrom, typeTo)

        override fun forIdentifier(identifier: String): ArchiveMapping {
            // Super expensive depending on length of path*
            val path = edgePath.map {
                DirectedMappingNode(
                    DirectedMappingType(it.from), DirectedMappingType(it.to),

//                    when (it.direction) {
//                        MappingDirection.TO_REAL -> DirectedMappingType.fake
//                        MappingDirection.TO_FAKE -> DirectedMappingType.real
//                    },
//                    when (it.direction) {
//                        MappingDirection.TO_REAL -> DirectedMappingType.real
//                        MappingDirection.TO_FAKE -> DirectedMappingType.fake
//                    },
                    it.provider.forIdentifier(identifier)
                )
            }

            return joinMappings(path)
        }
    }
}

public class DirectedMappingType internal constructor(
    public val namespace: String
) {
    // Attempt to get an identifier matching the current namespace, if it cant be found, go through the other namespaces
    // and look for a match (the only real reason we do this that there may be a mapping that does not change across namespaces
    // and if that is the case, it may not be present in other namespaces so we just keep looking -- I dont really like
    // this, so this may change in future versions of archive-mapper)
    public fun <T : MappingIdentifier> get(mappingNode: MappingNode<T>): T =
        mappingNode.getIdentifier(namespace) ?: mappingNode.namespaces
            .toMutableList().apply { remove(namespace) }
            .firstNotNullOf {
            mappingNode.getIdentifier(it)
        }
}

public data class DirectedMappingNode<T : MappingNode<*>>(
    val from: DirectedMappingType, val to: DirectedMappingType, val node: T
)

// Included out here for debugging
private fun <K, V> Collection<V>.doublyAssociateBy(
    keySelector: (V) -> Pair<K, K>
): Map<K, V> {
    return associateBy { keySelector(it).first } + associateBy { keySelector(it).second }
}

private fun <T, R, K> List<T>.foldingMap(
    initial: R, mapper: (R, T) -> Pair<K, R>
): Pair<List<K>, R> {
    var accumulator = initial

    return map {
        val pair = mapper(accumulator, it)
        accumulator = pair.second
        pair.first
    } to accumulator
}

//private fun <T : MappingIdentifier> T.assertIsType(type: MappingType): T {
//    if (this.type == type) return this
//
//    return when (this) {
//        is ClassIdentifier -> copy(type = type) as T
//        is MethodIdentifier -> copy(type = type) as T
//        is FieldIdentifier -> copy(type = type) as T
//        else -> throw IllegalArgumentException("Unknown mapping identifier: '$this'")
//    }
//}
//
//private fun <T : MappingIdentifier> T.assertIsReal(): T = assertIsType(REAL)
//private fun <T : MappingIdentifier> T.assertIsFake(): T = assertIsType(FAKE)

// We assume that the order given to us is from fake to real, thats how this archive will be built.
public fun joinMappings(path: List<DirectedMappingNode<ArchiveMapping>>): ArchiveMapping {
    if (path.size == 1) return path.first().node
    val fromNS = path.first().from.namespace
    val toNS = path.last().to.namespace
    val namespaces = setOf(fromNS, toNS)
    return ArchiveMapping(
        namespaces,
        MappingValueContainerImpl(namespaces.associateWith { n ->
            ArchiveIdentifier("", n)
        }),
        MappingNodeContainerImpl(path.first().node.classes.values
            .asSequence()
            .map { path.first().from.get(it) }
            .mapTo(HashSet()) { fromClassIdentifier: ClassIdentifier ->
                val (classes, toClassIdentifier: ClassIdentifier) = path.foldingMap(fromClassIdentifier) { acc, it ->
                    val node = it.node.classes[acc]
                        ?: throw IllegalStateException("Failed to follow reference chain from ${fromClassIdentifier.namespace} class: '${fromClassIdentifier.name}' to appropriate remapping.")

                    DirectedMappingNode(
                        it.from, it.to, node
                    ) to it.to.get(node)
                }

                ClassMapping(
                    namespaces,
                    MappingValueContainerImpl(
                        mapOf(
                            fromNS to fromClassIdentifier,
                            toNS to toClassIdentifier,
                        )
                    ),
                    MappingNodeContainerImpl(classes.first().node.methods.values.map { classes.first().from.get(it) }
                        .mapTo(HashSet()) { fromMethodIdentifier ->
                            val (methods, toMethodIdentifier) = classes.foldingMap(fromMethodIdentifier) { acc, it ->
                                val methodNode = it.node.methods[acc]
                                    ?: throw IllegalStateException("Failed to follow reference chain from ${fromMethodIdentifier.namespace} method: '${fromMethodIdentifier.name}' to appropriate remapping. In ${fromClassIdentifier.namespace} class '${fromClassIdentifier.name}'")

                                DirectedMappingNode(
                                    it.from, it.to, methodNode
                                ) to it.to.get(methodNode)
                            }

                            // By default fake is the first, real is the last
                            val (toRT, fromRT) = (run {
                                val directed = methods.last()
                                directed.node.returnType[directed.to.namespace]!!
//                                        when (directed.to.type) {
//                                            REAL -> directed.node.realReturnType
//                                            FAKE -> directed.node.fakeReturnType
//                                        }
                            } to run {
                                val directed = methods.first()
                                directed.node.returnType[directed.from.namespace]!!
//                                        when (directed.from.type) {
//                                            REAL -> directed.node.realReturnType
//                                            FAKE -> directed.node.fakeReturnType
//                                        }
                            })

                            MethodMapping(
                                namespaces,
                                MappingValueContainerImpl(
                                    mapOf(
                                        toNS to toMethodIdentifier,
                                        fromNS to fromMethodIdentifier,
                                    )
                                ),
                                methods.first().node.lnStart,
                                methods.first().node.lnEnd,
                                MappingValueContainerImpl(
                                    mapOf(
                                        toNS to toRT, fromNS to fromRT
                                    )
                                )
                            )
                        }),
                    MappingNodeContainerImpl(classes.first().node.fields.values.map { classes.first().from.get(it) }
                        .mapTo(HashSet()) { fromFieldIdentifier ->
                            val (fields, toFieldIdentifier) = classes.foldingMap(fromFieldIdentifier) { acc, it ->
                                val fieldNode = it.node.fields[acc]
                                    ?: throw IllegalStateException("Failed to follow reference chain from ${fromFieldIdentifier.namespace} field: '${fromFieldIdentifier.name}' to appropriate remapping. In ${fromClassIdentifier.namespace} class '${fromClassIdentifier.name}'")

                                DirectedMappingNode(
                                    it.from, it.to, fieldNode
                                ) to it.to.get(fieldNode)
                            }

                            val (toType, fromType) = run {
                                val directed = fields.last()

                                directed.node.type[directed.to.namespace]!!

//                                when (directed.to.type) {
//                                    REAL -> directed.node.toType
//                                    FAKE -> directed.node.fakeType
//                                }
                            } to run {
                                val directed = fields.first()

                                directed.node.type[directed.from.namespace]!!
//                                when (directed.from.type) {
//                                    REAL -> directed.node.toType
//                                    FAKE -> directed.node.fakeType
//                                }
                            }

                            FieldMapping(
                                namespaces,
                                MappingValueContainerImpl(
                                    mapOf(
                                        toNS to toFieldIdentifier,
                                        fromNS to fromFieldIdentifier,
                                    )
                                ),
                                MappingValueContainerImpl(
                                    mapOf(
                                        toNS to toType,
                                        fromNS to fromType
                                    )
                                ),
                            )
                        })
                )
            })
    )
}