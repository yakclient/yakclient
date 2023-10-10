package net.yakclient.components.extloader.test.mapping

import net.yakclient.archive.mapper.*
import net.yakclient.archive.mapper.MappingType.FAKE
import net.yakclient.archive.mapper.MappingType.REAL
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.components.extloader.mapping.*
import net.yakclient.components.extloader.api.mapping.MappingsProvider
import java.lang.IllegalStateException
import kotlin.math.exp
import kotlin.test.Test


class TestMappingGraph {
    val mapping1 = ArchiveMapping(
        listOf(
            ClassMapping(
                ClassIdentifier("FirstReal", REAL),
                ClassIdentifier("FirstFake", FAKE),
                listOf(
                    MethodMapping(
                        MethodIdentifier("FirstRealMethod", listOf(), REAL),
                        MethodIdentifier("FirstFakeMethod", listOf(), FAKE),
                        0, 0, 0, 0,
                        PrimitiveTypeIdentifier.BOOLEAN,
                        PrimitiveTypeIdentifier.INT,
                    )
                ).toMap(),
                listOf(
                    FieldMapping(
                        FieldIdentifier("FirstRealField", REAL),
                        FieldIdentifier("FirstFakeField", FAKE),
                        PrimitiveTypeIdentifier.BOOLEAN,
                        PrimitiveTypeIdentifier.INT,
                    )
                ).toMap()
            )
        ).toMap()
    )
    val mapping2 = ArchiveMapping(
        listOf(
            ClassMapping(
                ClassIdentifier("FirstReal", REAL),
                ClassIdentifier("SecondFake", FAKE),
                listOf(
                    MethodMapping(
                        MethodIdentifier("FirstRealMethod", listOf(), REAL),
                        MethodIdentifier("SecondFakeMethod", listOf(), FAKE),
                        0, 0, 0, 0,
                        PrimitiveTypeIdentifier.BOOLEAN,
                        PrimitiveTypeIdentifier.INT,
                    )
                ).toMap(),
                listOf(
                    FieldMapping(
                        FieldIdentifier("FirstRealField", REAL),
                        FieldIdentifier("SecondFakeField", FAKE),
                        PrimitiveTypeIdentifier.BOOLEAN,
                        PrimitiveTypeIdentifier.INT,
                    )
                ).toMap()
            )
        ).toMap()
    )

    val mapping3 = ArchiveMapping(
        listOf(
            ClassMapping(
                ClassIdentifier("SecondFake", REAL),
                ClassIdentifier("ThirdFake", FAKE),
                listOf(
                    MethodMapping(
                        MethodIdentifier("SecondFakeMethod", listOf(), REAL),
                        MethodIdentifier("ThirdFakeMethod", listOf(), FAKE),
                        0, 0, 0, 0,
                        PrimitiveTypeIdentifier.BOOLEAN,
                        PrimitiveTypeIdentifier.INT,
                    )
                ).toMap(),
                listOf(
                    FieldMapping(
                        FieldIdentifier("SecondFakeField", REAL),
                        FieldIdentifier("ThirdFakeField", FAKE),
                        PrimitiveTypeIdentifier.BOOLEAN,
                        PrimitiveTypeIdentifier.INT,
                    )
                ).toMap()
            )
        ).toMap()
    )

    val expected = ArchiveMapping(
        listOf(
            ClassMapping(
                ClassIdentifier("ThirdFake", REAL),
                ClassIdentifier("FirstFake", FAKE),
                listOf(
                    MethodMapping(
                        MethodIdentifier("ThirdFakeMethod", listOf(), REAL),
                        MethodIdentifier("FirstFakeMethod", listOf(), FAKE),
                        0, 0, 0, 0,
                        PrimitiveTypeIdentifier.INT,
                        PrimitiveTypeIdentifier.INT
                    )
                ).toMap(),
                listOf(
                    FieldMapping(
                        FieldIdentifier("ThirdFakeField", REAL),
                        FieldIdentifier("FirstFakeField", FAKE),
                        PrimitiveTypeIdentifier.INT,
                        PrimitiveTypeIdentifier.INT
                    )
                ).toMap()
            )
        ).toMap()
    )

    private fun <K : MappingIdentifier, V : MappingNode<K>> List<V>.toMap(): Map<K, V> {
        return associateBy { it.realIdentifier } + associateBy { it.fakeIdentifier }
    }

    @Test
    fun `Test joining 1 mappings returns same thing`() {
        val path = listOf(
            DirectedMappingNode(
                DirectedMappingType.fake,
                DirectedMappingType.real,
                mapping1
            )
        )

        val joined = joinMappings(path)
        check(joined == mapping1)
    }

    // Basic example of fake-real to real-fake to real-fake
    @Test
    fun `Test joining multiple mappings returns correctly`() {
        val path = listOf(
            DirectedMappingNode(
                DirectedMappingType.fake,
                DirectedMappingType.real,
                mapping1
            ),
            DirectedMappingNode(
                DirectedMappingType.real,
                DirectedMappingType.fake,
                mapping2
            ),
            DirectedMappingNode(
                DirectedMappingType.real,
                DirectedMappingType.fake,
                mapping3
            )
        )

        val joined = joinMappings(path)

        check(joined == expected)
    }

    fun createProvider(
        real: String,
        fake: String,
        mappingProvider: (String) -> ArchiveMapping? = { null }
    ): MappingsProvider = object : MappingsProvider {
        override val realType: String = real
        override val fakeType: String = fake

        override fun forIdentifier(identifier: String): ArchiveMapping {
            return mappingProvider(identifier) ?: throw IllegalStateException("This is not suppose to be called!")
        }
    }

    @Test
    fun `Test graph produces correct edges`() {
        val graph = newMappingsGraph(
            listOf(
                createProvider("First", "Second"),
                createProvider("Second", "Third"),
                createProvider("First", "Fourth"),
                createProvider("Third", "Fourth"),
            )
        )

        fun assertEdges(vertex: String, vararg outgoing: Pair<String, MappingDirection>) {
            val edges = graph.connectingEdges(vertex)

            outgoing.forEach { (vertex, direction) ->
                check(edges.any {
                    when (it.direction) {
                        MappingDirection.TO_REAL -> it.provider.realType
                        MappingDirection.TO_FAKE -> it.provider.fakeType
                    } == vertex && direction == it.direction
                })
            }
        }

        assertEdges(
            "First",
            "Second" to MappingDirection.TO_FAKE,
            "Fourth" to MappingDirection.TO_FAKE
        )

        assertEdges(
            "Second",
            "First" to MappingDirection.TO_REAL,
            "Third" to MappingDirection.TO_FAKE
        )

        assertEdges(
            "Fourth",
            "First" to MappingDirection.TO_REAL,
            "Third" to MappingDirection.TO_REAL
        )
    }

    @Test
    fun `Test graph and archive creation`() {
        val graph = newMappingsGraph(
            listOf(
                createProvider("Second", "First") {mapping1},
                createProvider("Second", "Third") {mapping2},
                createProvider("Third", "Fourth") {mapping3}
            )
        )

        val output =graph.findShortest("First", "Fourth")

        check(output.forIdentifier("")  == expected)
    }
}