package net.yakclient.components.yak.test.extension

import net.yakclient.archive.mapper.*
import net.yakclient.archive.mapper.MappingType.*
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.mapMethodSignature
import net.yakclient.archive.mapper.transform.mapType
import net.yakclient.archive.mapper.transform.transformClass
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.common.util.openStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.tree.*
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.net.URI
import kotlin.test.Test
import net.yakclient.components.yak.mapping.*;
import org.objectweb.asm.ClassWriter
import java.net.URL

class TestTypeMapping {
    private fun printAndCheck(any: Any, other: Any) {
        println(any)
        check(any == other)
    }

    @Test
    fun `Test single type mapping`() {
        val parser = net.yakclient.archive.mapper.parsers.ProGuardMappingParser

        val mappings =
            parser.parse(URL("https://launcher.mojang.com/v1/objects/a661c6a55a0600bd391bdbbd6827654c05b2109c/client.txt").openStream())

        printAndCheck(mappings.mapType("C", MappingDirection.TO_FAKE), "C")
        printAndCheck(mappings.mapType("Ljava/lang/String;", MappingDirection.TO_FAKE), "Ljava/lang/String;")
        printAndCheck(mappings.mapType("Lcom.mojang.blaze3d.platform.InputConstants;", MappingDirection.TO_FAKE), "Ldsh;")
        printAndCheck(mappings.mapType("[[[Lcom.mojang.blaze3d.platform.InputConstants;", MappingDirection.TO_FAKE), "[[[Ldsh;")
    }

    @Test
    fun `Test map signature`() {
        val parser = net.yakclient.archive.mapper.parsers.ProGuardMappingParser

        val mappings =
            parser.parse(URI("https://launcher.mojang.com/v1/objects/a661c6a55a0600bd391bdbbd6827654c05b2109c/client.txt").openStream())

        printAndCheck(mappings.mapMethodSignature("net/minecraft/client/gui/screens/TitleScreen", "init()V", MappingDirection.TO_FAKE), "b()V")
    }


    @Test
    fun `Test full class type map`() {
        val thingMethod = MethodMapping(
            MethodIdentifier("doRealThing", listOf(), REAL),
            MethodIdentifier("doFakeThing", listOf(), FAKE),
            null,
            null,
            null,
            null,
            PrimitiveTypeIdentifier.VOID,
            PrimitiveTypeIdentifier.VOID,
        )

        val somethingMethod = MethodMapping(
            MethodIdentifier(
                "doSomethingElse",
                listOf(ArrayTypeIdentifier(ClassTypeIdentifier("java/lang/String"))),
                REAL
            ),
            MethodIdentifier(
                "doOtherFakeThing",
                listOf(ArrayTypeIdentifier(ClassTypeIdentifier("java/lang/String"))),
                FAKE
            ),
            null,
            null,
            null,
            null,
            PrimitiveTypeIdentifier.VOID,
            PrimitiveTypeIdentifier.VOID,
        )

        val stringValueField = FieldMapping(
            FieldIdentifier("realStringValue", REAL),
            FieldIdentifier("fakeStringValue", FAKE),
            ClassTypeIdentifier("java/lang/String"),
            ClassTypeIdentifier("java/lang/String")

        )
        val classMapping = ClassMapping(
            ClassIdentifier(
                "net/yakclient/components/yak/test/extension/RealClass", REAL
            ),
            ClassIdentifier("net/yakclient/components/yak/test/extension/FakeClass", FAKE),
            mapOf(
                thingMethod.realIdentifier to thingMethod,
                thingMethod.fakeIdentifier to thingMethod,
                somethingMethod.realIdentifier to somethingMethod,
                somethingMethod.fakeIdentifier to somethingMethod
            ),

            mapOf(
                stringValueField.realIdentifier to stringValueField,
                stringValueField.fakeIdentifier to stringValueField
            )
        )

        val exceptionMapping = ClassMapping(
            ClassIdentifier(
                "net/yakclient/components/yak/test/extension/RealException", REAL
            ),
            ClassIdentifier("net/yakclient/components/yak/test/extension/FakeException", FAKE),
            mapOf(),
            mapOf()
        )
        val mappings = ArchiveMapping(
            mapOf(
                classMapping.realIdentifier to classMapping,
                classMapping.fakeIdentifier to classMapping,
                exceptionMapping.realIdentifier to exceptionMapping,
                exceptionMapping.fakeIdentifier to exceptionMapping
            )
        )




        val classloader = object : ClassLoader(this::class.java.classLoader) {
            var loaded = false
            override fun loadClass(name: String?): Class<*> {
                if (name == ToTransform::class.java.name && !loaded) {
                    loaded = true

                    val resolve = transformClass(
                        ClassReader(ToTransform::class.java.name),
                        mappings,
                        MappingDirection.TO_FAKE,
                        ClassWriter(0)
                    )
                    return defineClass(ToTransform::class.java.name, resolve, 0, resolve.size)
                }

                return super.loadClass(name)
            }
        }

        val cls = classloader.loadClass(ToTransform::class.java.name)
        cls.getMethod("doSomething")
            .also(Method::trySetAccessible)
            .invoke(cls.getConstructor().also(Constructor<*>::trySetAccessible).newInstance())
    }
}

class FakeClass {
    @JvmField
    val fakeStringValue: String = "This is probably not real"

    fun doFakeThing() {
        println("This is fake!!")
    }

    fun doOtherFakeThing(vararg params: String) {
        println(params.joinToString() + " This is fake!")
    }
}

class RealClass {
    @JvmField
    val realStringValue: String = "This is real"

    fun doRealThing() {
        println("This is real!")
    }

    fun doSomethingElse(vararg params: String) {
        println(params.joinToString())
    }
}

data class FakeException(
    override val message: String
) : Exception()

data class RealException(
    override val message: String
) : Exception()

class ToTransform(
    val value: RealClass = RealClass()
) {
    fun doSomething() {
        value.apply {
            value.doSomethingElse("YAYA", "AHAH", "!!!!", "??")
        }

        for (i in 1..10) {
            value.doSomethingElse("$i")
        }

        println(value)
        value.doRealThing()

        println(value.realStringValue)

        try {
            doSomethingWithAValue(value)
        } catch (e: RealException) {
            println(e)
        }
    }

    fun doSomethingWithAValue(cls: RealClass) {
        println(cls::class.java.name)

        throw RealException("Idk, i just wanted to throw this")
    }
}