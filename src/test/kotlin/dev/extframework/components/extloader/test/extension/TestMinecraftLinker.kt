package dev.extframework.components.extloader.test.extension

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.boot.loader.*
import dev.extframework.common.util.immutableLateInit
import dev.extframework.components.extloader.target.TargetLinker
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.net.URL
import java.nio.ByteBuffer
import java.security.ProtectionDomain
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestMinecraftLinker {
    private fun setupClassProvider(packages: Set<String>, delegate: (n: String) -> Class<*>?): ClassProvider = object : ClassProvider {
        override val packages: Set<String> = packages

        override fun findClass(name: String): Class<*>? {
            return delegate(name)
        }

    }

    private fun setupResourceProvider(delegate: (n: String) -> URL?): ResourceProvider = object : ResourceProvider {
        override fun findResources(name: String): Sequence<URL> {
            return delegate(name)?.let { sequenceOf(it) } ?: emptySequence()
        }
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

        var mcClassProvider : ClassProvider by immutableLateInit()
        var mcResourceProvider : ResourceProvider by immutableLateInit()

        val extProvider: ClassProvider = setupClassProvider(setOf("")) { name ->
            extClasses.find { it.name == name } ?: mcClassProvider.findClass(name)
        }

        val extSource: ResourceProvider =setupResourceProvider {
            extResources[it]
        }

        val linker = TargetLinker(
            SimpleMavenDescriptor.parseDescription("net.minecraft:minecraft:nan")!!,

            setupClassProvider(setOf("")) { n ->
                mcClasses.find { it.name == n } ?: extProvider.findClass(n)
            },
            setupResourceProvider {
                mcResources[it] //?: extSource.findResources(it).firstOrNull()
            },
            MutableClassProvider(ArrayList()),
            MutableResourceProvider(ArrayList())
        )

        mcClassProvider = linker.targetTarget.relationship.classes
        mcResourceProvider = linker.targetTarget.relationship.resources

        linker.addMiscClasses(extProvider)
        linker.addMiscResources(extSource)

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

        assertNotNull(linker.miscTarget.relationship.classes.findClass("a")) { "Failed to find class 'a' in extensions" }

        assertNotNull(linker.targetTarget.relationship.classes.findClass("b")) { "In minecraft!" }
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
            linker.miscTarget.relationship.classes.findClass("c")
        }.also {
            assert(it.isFailure && it.exceptionOrNull() is ClassNotFoundException) { "Extensions Should have thrown class not found!" }
        }

        runCatching {
            linker.targetTarget.relationship.classes.findClass("e")
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

        assertNotNull(linker.miscTarget.relationship.resources.findResources("first.json").firstOrNull()) { "Couldn't find resource: 'first.json' in extensions " }
        assertNotNull(linker.targetTarget.relationship.resources.findResources("second.json").firstOrNull()) { "Couldn't find resource: 'second.json' in minecraft " }
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

        assert(linker.miscTarget.relationship.resources.findResources("third.json").toList().isEmpty()) { "This should never happen" }
        assert(linker.targetTarget.relationship.resources.findResources("third.json").toList().isEmpty()) { "This should never happen" }
    }
}