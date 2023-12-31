package net.yakclient.components.extloader.test.extension

import net.yakclient.boot.loader.ClassProvider
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.common.util.immutableLateInit
import net.yakclient.components.extloader.target.TargetLinker
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.net.URL
import java.nio.ByteBuffer
import java.security.ProtectionDomain
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestMinecraftLinker {
    private fun setupClassProvider(delegate: (n: String) -> Class<*>?): ClassProvider = object : ClassProvider {
        override val packages: Set<String> = HashSet()

        override fun findClass(name: String): Class<*>? {
            return delegate(name)
        }

        override fun findClass(name: String, module: String): Class<*>? {
            return findClass(name)
        }
    }

    private fun setupSourceProvider(delegate: (n: String) -> URL?): SourceProvider = object : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun getResource(name: String): URL? {
            return delegate(name)
        }

        override fun getResource(name: String, module: String): URL? = getResource(name)

        override fun getSource(name: String): ByteBuffer? = null
    }

    private fun newClass(
            name: String
    ): Class<*> {
        val classNode = ClassNode()
        classNode.visit(
                61, Opcodes.ACC_PUBLIC,
                name,
                null,
                "java/lang/Object",
                arrayOf()
        )

        val writer = ClassWriter(0)
        classNode.accept(writer)


        val loader = object : ClassLoader() {
            val theClass: Class<*> = defineClass(
                    name,
                    ByteBuffer.wrap(writer.toByteArray()), ProtectionDomain(null, null)
            )
        }

        return loader.theClass
    }


    private fun setupLinker(
            extClasses: List<Class<*>> = listOf(),
            mcClasses: List<Class<*>> = listOf(),
            extResources: Map<String, URL> = mapOf(),
            mcResources: Map<String, URL> = mapOf(),
    ): TargetLinker {
        var mcProvider: ClassProvider by immutableLateInit()
        var extProvider: ClassProvider by immutableLateInit()

        var extSource: SourceProvider by immutableLateInit()
        var mcSource: SourceProvider by immutableLateInit()

        val linker = TargetLinker(
                setupClassProvider { n ->
                    mcClasses.find { it.name == n } ?: extProvider.findClass(n)
                },
                setupSourceProvider {
                    mcResources[it] ?: extSource.getResource(it)
                }
        )

        linker.addMiscClasses(extProvider)
        linker.addMiscSources(extSource)

        mcProvider = linker.targetClassProvider
        extProvider = linker.miscClassProvider

        extSource = linker.miscSourceProvider
        mcSource = linker.targetSourceProvider

        return linker
    }

    @Test
    fun `Test will load each others classes`() {
        val linker = setupLinker(
                listOf(
                        newClass("a")
                ),
                listOf(
                        newClass("b")
                )
        )

        assertNotNull(linker.miscClassProvider.findClass("a")) { "Failed to find class 'a' in extensions" }

        assertNotNull(linker.targetClassProvider.findClass("b")) { "In minecraft!" }
    }

    @Test
    fun `Test will throw class not found when no class can be found`() {
        val linker = setupLinker(
                listOf(
                        newClass("a")
                ),
                listOf(
                        newClass("b")
                )
        )

        runCatching {
            linker.miscClassProvider.findClass("c")
        }.also {
            assert(it.isFailure && it.exceptionOrNull() is ClassNotFoundException) { "Extensions Should have thrown class not found!" }
        }

        runCatching {
            linker.targetClassProvider.findClass("e")
        }.also {
            assert(it.isFailure && it.exceptionOrNull() is ClassNotFoundException) { "Minecraft Should have thrown class not found!" }
        }
    }

    @Test
    fun `Test will load each others resources`() {
        val linker = setupLinker(
                extResources = mapOf(
                        "first.json" to URL("file:///first.json"),
                ),
                mcResources = mapOf(
                        "second.json" to URL("file:///second.json")
                )
        )

        assertNotNull(linker.miscSourceProvider.getResource("first.json")) {"Couldn't find resource: 'first.json' in extensions "}
        assertNotNull(linker.targetSourceProvider.getResource("second.json")) {"Couldn't find resource: 'second.json' in minecraft "}
    }

    @Test
    fun `Test will throw when resources are not found`() {
        val linker = setupLinker(
                extResources = mapOf(
                        "first.json" to URL("file:///first.json"),
                ),
                mcResources = mapOf(
                        "second.json" to URL("file:///second.json")
                )
        )

        assert(linker.miscSourceProvider.getResource("third.json") == null) { "This should never happen"}
        assert(linker.targetSourceProvider.getResource("third.json") == null) { "This should never happen"}
    }
}