package net.yakclient.components.extloader.mapping

import net.yakclient.archive.mapper.*
import net.yakclient.archive.mapper.MappingType.*
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.components.extloader.api.mapping.MappingsProvider
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet


public interface MappingsGraph {
    public data class ProviderEdge(
        val direction: MappingDirection,
        val provider: MappingsProvider
    )

    public fun connectingEdges(type: String): List<ProviderEdge>
}

public fun newMappingsGraph(list: List<MappingsProvider>): MappingsGraph = object : MappingsGraph {
    val outEdges: Map<String, List<MappingsGraph.ProviderEdge>> = list

        .map { MappingsGraph.ProviderEdge(MappingDirection.TO_FAKE, it) }
        .groupBy { it.provider.realType } combine list

        .map { MappingsGraph.ProviderEdge(MappingDirection.TO_REAL, it) }
        .groupBy { it.provider.fakeType }

    // ChatGPT wrote this one lol, not super functional oriented (not that kotlin is itself) but a good design.
    private infix fun <K, V> Map<K, List<V>>.combine(other: Map<K, List<V>>): Map<K, List<V>> {
        val result = mutableMapOf<K, MutableList<V>>()

        // Add all entries from the first map
        for ((key, value) in this) {
            result[key] = value.toMutableList()
        }

        // Add entries from the second map, merging lists if keys collide
        for ((key, value) in other) {
            if (result.containsKey(key)) {
                result[key]?.addAll(value)
            } else {
                result[key] = value.toMutableList()
            }
        }

        return result
    }

    override fun connectingEdges(type: String): List<MappingsGraph.ProviderEdge> {
        return outEdges[type] ?: listOf()
    }
}

// BFS
public fun MappingsGraph.findShortest(
    typeFrom: String,
    typeTo: String,
): MappingsProvider {
    fun MappingsGraph.ProviderEdge.to(): String =
        when (direction) {
            MappingDirection.TO_REAL -> provider.realType
            MappingDirection.TO_FAKE -> provider.fakeType
        }

    fun MappingsGraph.ProviderEdge.from(): String =
        when (direction) {
            MappingDirection.TO_REAL -> provider.fakeType
            MappingDirection.TO_FAKE -> provider.realType
        }

    if (typeFrom == typeTo) return object : MappingsProvider {
        override val realType: String = typeFrom
        override val fakeType: String = typeTo

        override fun forIdentifier(identifier: String): ArchiveMapping = ArchiveMapping(HashMap())
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

        if (current == typeTo) return createProviderFrom(edgeTo, typeTo,typeFrom)

        for (currentEdge in connectingEdges(current)) {
            perimeter.add(currentEdge.to())

            val currentDist = distTo[currentEdge.to()] ?: Int.MAX_VALUE
            val newDist = distTo[currentEdge.from()]!! + 1

            if (newDist < currentDist) {
                distTo[currentEdge.to()] = newDist
                edgeTo[currentEdge.to()] = currentEdge
            }
        }

        visited.add(current)
    }

    throw IllegalStateException("Failed to find path between mappings")
}

private fun createProviderFrom(edges: Map<String, MappingsGraph.ProviderEdge>, typeTo: String, typeFrom: String) : MappingsProvider {
    fun MappingsGraph.ProviderEdge.from(): String =
        when (direction) {
            MappingDirection.TO_REAL -> provider.fakeType
            MappingDirection.TO_FAKE -> provider.realType
        }

    fun createPath(vertex: String): List<MappingsGraph.ProviderEdge> {
        val edge = edges[vertex] ?: return listOf()
        return createPath(edge.from()) + edge
    }

    val edgePath = createPath(typeTo)
    return object : MappingsProvider {
        override val realType: String = typeTo
        override val fakeType: String = typeFrom

        override fun forIdentifier(identifier: String): ArchiveMapping {
            // Super expensive depending on length of path*
            val path = edgePath.map {
                DirectedMappingNode(
                    when (it.direction) {
                        MappingDirection.TO_REAL -> DirectedMappingType.fake
                        MappingDirection.TO_FAKE -> DirectedMappingType.real
                    },
                    when (it.direction) {
                        MappingDirection.TO_REAL -> DirectedMappingType.real
                        MappingDirection.TO_FAKE -> DirectedMappingType.fake
                    },
                    it.provider.forIdentifier(identifier)
                )
            }

            return joinMappings(path)
        }
    }
}

public class DirectedMappingType private constructor(
    public val type: MappingType
) {
    public fun <T : MappingIdentifier> get(mappingNode: MappingNode<T>): T = when (type) {
        REAL -> mappingNode.realIdentifier
        FAKE -> mappingNode.fakeIdentifier
    }

    public companion object {
        public val real: DirectedMappingType = DirectedMappingType(REAL)
        public val fake: DirectedMappingType = DirectedMappingType(FAKE)

    }
}

public data class DirectedMappingNode<T : MappingNode<*>>(
    val from: DirectedMappingType,
    val to: DirectedMappingType,
    val node: T
)

// We assume that the order given to us is from fake to real, thats how this archive will be built.
public fun joinMappings(path: List<DirectedMappingNode<ArchiveMapping>>): ArchiveMapping {
    if (path.size == 1) return path.first().node

    fun <K, V> Collection<V>.doublyAssociateBy(
        keySelector: (V) -> Pair<K, K>
    ): Map<K, V> {
        return associateBy { keySelector(it).first } + associateBy { keySelector(it).second }
    }

    fun <T, R, K> List<T>.foldingMap(
        initial: R,
        mapper: (R, T) -> Pair<K, R>
    ): Pair<List<K>, R> {
        var accumulator = initial

        return map {
            val pair = mapper(accumulator, it)
            accumulator = pair.second
            pair.first
        } to accumulator
    }

    fun <T : MappingIdentifier> T.assertIsType(type: MappingType): T {
        if (this.type == type) return this

        return when (this) {
            is ClassIdentifier -> copy(type = type) as T
            is MethodIdentifier -> copy(type = type) as T
            is FieldIdentifier -> copy(type = type) as T
            else -> throw IllegalArgumentException("Unknown mapping identifier: '$this'")
        }
    }

    fun <T : MappingIdentifier> T.assertIsReal(): T = assertIsType(REAL)
    fun <T : MappingIdentifier> T.assertIsFake(): T = assertIsType(FAKE)

    return ArchiveMapping(
        path.first().node.classes
            .values
            .toSet()
            .map { path.first().from.get(it) }
            .map { fakeClassIdentifier ->
                val (classes, realClassIdentifier: ClassIdentifier) = path.foldingMap(fakeClassIdentifier) { acc, it ->
                    val node = it.node.classes[acc.assertIsType(it.from.type)] ?: throw IllegalStateException("")

                    DirectedMappingNode(
                        it.from,
                        it.to,
                        node
                    ) to it.to.get(node)
                }

                ClassMapping(
                    realClassIdentifier.assertIsReal(),
                    fakeClassIdentifier.assertIsFake(),
                    classes.first().node.methods
                        .values
                        .toSet()
                        .map { classes.first().from.get(it) }
                        .map { fakeMethodIdentifier ->
                            val (methods, realMethodIdentifier) = classes.foldingMap(fakeMethodIdentifier) { acc, it ->
                                val methodNode =
                                    it.node.methods[acc.assertIsType(it.from.type)] ?: throw IllegalStateException("")

                                DirectedMappingNode(
                                    it.from,
                                    it.to,
                                    methodNode
                                ) to it.to.get(methodNode)
                            }

                            // By default fake is the first, real is the last
                            val (realReturnType, fakeReturnType) = (run {
                                val directed = methods.last()
                                when (directed.to.type) {
                                    REAL -> directed.node.realReturnType
                                    FAKE -> directed.node.fakeReturnType
                                }
                            } to run {
                                val directed = methods.first()
                                when (directed.from.type) {
                                    REAL -> directed.node.realReturnType
                                    FAKE -> directed.node.fakeReturnType
                                }
                            })

                            MethodMapping(
                                realMethodIdentifier.assertIsReal(),
                                fakeMethodIdentifier.assertIsFake(),
                                methods.first().node.lnStart,
                                methods.first().node.lnEnd,
                                methods.first().node.originalLnStart,
                                methods.first().node.originalLnEnd,
                                realReturnType, fakeReturnType
                            )
                        }.doublyAssociateBy { it.realIdentifier to it.fakeIdentifier },
                    classes.first().node.fields
                        .values
                        .toSet()
                        .map { classes.first().from.get(it) }
                        .map { fakeFieldIdentifier ->
                            val (fields, realFieldIdentifier) = classes.foldingMap(fakeFieldIdentifier) { acc, it ->
                                val fieldNode = it.node.fields[acc.assertIsType(it.from.type)]
                                    ?: throw IllegalStateException("")

                                DirectedMappingNode(
                                    it.from,
                                    it.to,
                                    fieldNode
                                ) to it.to.get(fieldNode)
                            }

                            val (realType, fakeType) =
                                run {
                                    val directed = fields.last()
                                    when (directed.to.type) {
                                        REAL -> directed.node.realType
                                        FAKE -> directed.node.fakeType
                                    }
                                } to run {
                                    val directed = fields.first()
                                    when (directed.from.type) {
                                        REAL -> directed.node.realType
                                        FAKE -> directed.node.fakeType
                                    }
                                }

                            FieldMapping(
                                realFieldIdentifier.assertIsReal(), fakeFieldIdentifier.assertIsFake(),
                                realType, fakeType
                            )
                        }.doublyAssociateBy { it.realIdentifier to it.fakeIdentifier }
                )
            }.doublyAssociateBy { it.realIdentifier to it.fakeIdentifier })
}