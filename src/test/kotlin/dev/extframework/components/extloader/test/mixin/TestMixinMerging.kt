package dev.extframework.components.extloader.test.mixin

import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

class TestMixinMerging {
    val initialNode = ClassNode()

    init {
        initialNode.interfaces = listOf("classA", "classB")
        initialNode.methods.add(MethodNode().also {
            it.name = "methodA"
            it.desc = "()V"
            it.instructions.add(InsnNode(Opcodes.RETURN))
        })
    }

    @Test
    fun `Test merge mixins`() {
        listOf("A", "B", "C", "D")
        listOf("A", "4", "D1", "C") // 1
        listOf("A1", "B", "D2") // 2

        // expected:
        // 1: Removed B, Added 4, Removed C, Added D1, removed D, added C
        // 2: Removed A, Added A1, Removed C, Removed D, Added D2
        // Output: A1, 4, D1, D2

        // 1:
        // A matches, continue
        // B and 4 dont,
        // C and 4 dont,
        // D and 4 dont, end of list, add 4
        // B and D1 dont
        // C and d1 dont,
        // D and D1 dont, end of list, add d1
        // B and C dont
        // C and C do, continue
        // End of list, B, and D unused, Remove B and D

        data class BasicTestType(
            val name: String,
            val instructions: List<Int>
        )

        listOf(
            BasicTestType("A", listOf(10, 5, 6)),
            BasicTestType("B", listOf()),
            BasicTestType("C", listOf(1, 5)),
            BasicTestType("D", listOf(1, 2, 3, 9, 8)),
        )
        listOf(
            BasicTestType("A", listOf(10, 5, 6)),
            BasicTestType("B", listOf()),
            BasicTestType("C", listOf(1, 5)),
            BasicTestType("E1", listOf(0, 0, 0)),
            BasicTestType("E2", listOf(1, 2, 3, 9, 8)),
            BasicTestType("D", listOf(5, 3, 2)),
        ) // 1
        listOf(
            BasicTestType("C", listOf(1, 5, 7, 6)),
        ) // 2

        // What makes sense for the output to be:
        // C(1,5,7,6), E1(same), E2(same)

        //Algorithmically:
        // 1: _, _, _ Add E1, Add E2, _
        // 2: Remove A, Remove B, Remove C, Update C (
        // _, _, Add  7, Add 6
        // ), Remove D,
        // Output: C (1,5,7,6), E1, E2


    }

    sealed interface MergingOperation<T> {

    }



    interface TypeMerger<T> {
        val type: Class<T>

        fun requiredOperation(
            matcher: TypeTester<*>,
            type1: T,
            type2: T
        ) : Boolean

        fun merge(
            type1: T,
            type2: T
        ): T
    }
}
typealias TypeTester<T> = (first: T, second: T) -> Boolean
