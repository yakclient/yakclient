package net.yakclient.components.yak.test.extension

import net.yakclient.archive.mapper.*
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.ByteCodeUtils
import net.yakclient.archives.transform.TransformerConfig
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.tree.*
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.net.URI
import kotlin.test.Test
import net.yakclient.components.yak.mapping.*;

class TestTypeMapping {
    private fun printAndCheck(any: Any, other: Any) {
        println(any)
        check(any == other)
    }

    @Test
    fun `Test single type mapping`() {


        val parser = Parsers[Parsers.PRO_GUARD]!!

        val mappings =
            parser.parse(URI("https://launcher.mojang.com/v1/objects/a661c6a55a0600bd391bdbbd6827654c05b2109c/client.txt"))

        fun mapType(type: String): String =
            if (type.isEmpty()) type
            else if (ByteCodeUtils.isPrimitiveType(type.first())) type
            else if (type.startsWith("[")) {
                "[" + mapType(type.substring(1 until type.length))
            } else {
                val byReal = mappings.classes.getByReal(type.trim('L', ';'))
                byReal?.fakeName?.let { "L$it;" } ?: type
            }

        printAndCheck(mapType("C"), "C")
        printAndCheck(mapType("Ljava/lang/String;"), "Ljava/lang/String;")
        printAndCheck(mapType("Lcom.mojang.blaze3d.platform.InputConstants;"), "Ldsh;")
        printAndCheck(mapType("[[[Lcom.mojang.blaze3d.platform.InputConstants;"), "[[[Ldsh;")
    }

    @Test
    fun `Test map signature`() {
        val parser = Parsers[Parsers.PRO_GUARD]!!

        val mappings =
            parser.parse(URI("https://launcher.mojang.com/v1/objects/a661c6a55a0600bd391bdbbd6827654c05b2109c/client.txt"))

        printAndCheck(mappings.mapMethodSignature("net/minecraft/client/gui/screens/TitleScreen", "init()V"), "b()V")
    }


    @Test
    fun `Test full class type map`() {
        val mappings = MappedArchive(
            "",
            "",
            ObfuscationMap(
                listOf(
                    MappedClass(
                        "net/yakclient/components/yak/test/extension/RealClass",
                        "net/yakclient/components/yak/test/extension/FakeClass",
                        ObfuscationMap(
                            mapOf(
                               "doRealThing()V" to "doFakeThing()V" to MappedMethod(
                                    "doRealThing",
                                    "doFakeThing",
                                    null,
                                    null,
                                    null,
                                    null,
                                    listOf(),
                                    PrimitiveTypeDescriptor.VOID,
                                ),
                                "doSomethingElse([Ljava/lang/String;)V" to "doOtherFakeThing([Ljava/lang/String;)V" to MappedMethod(
                                    "doSomethingElse",
                                    "doOtherFakeThing",
                                    null,
                                    null,
                                    null,
                                    null,
                                    listOf(
                                        ArrayTypeDescriptor(
                                            ClassTypeDescriptor(
                                                "java/lang/String"
                                            )
                                        )
                                    ),
                                    PrimitiveTypeDescriptor.VOID,
                                )
                            )
                        ),
                        ObfuscationMap(
                            listOf(
                                MappedField(
                                    "realStringValue",
                                    "fakeStringValue",
                                    ClassTypeDescriptor("java.lang.String")
                                )
                            )
                        )
                    ),
                    MappedClass(
                        "net.yakclient.components.yak.test.extension.RealException",
                        "net.yakclient.components.yak.test.extension.FakeException",
                        ObfuscationMap(),
                        ObfuscationMap()
                    )
                )
            )
        )

        val config = TransformerConfig.of {
            transformClass {
                it
            }

            transformField { node ->
                node.desc =  mappings.mapType(node.desc)

                node
            }

            transformMethod { node ->
                node.desc = mappings.mapMethodDesc(node.desc)

                node.exceptions = node.exceptions.map( mappings::mapType)

                node.localVariables.forEach {
                    it.desc =  mappings.mapType(it.desc)
                }

                node.tryCatchBlocks.forEach {
                    it.type =  mappings.mapClassName(it.type)
                }

                // AbstractInsnNode
                node.instructions.forEach {
                    when (it) {
                        is FieldInsnNode -> {
                            val mapClassName =  mappings.mapClassName(it.owner)
                            it.name = run {
                                mappings.getMappedClass(it.owner)
                                    ?.fields
                                    ?.getByReal(it.name)
                                    ?.fakeName
                                    ?: it.name
                            }
                            it.owner = mapClassName
                            it.desc =  mappings.mapType(it.desc)
                        }
                        is InvokeDynamicInsnNode -> {
                            // Can ignore name because only the name of the bootstrap method is known at compile time and that is held in the handle field
                            it.desc =  mappings.mapMethodDesc(it.desc) // Expected descriptor type of the generated call site

                            val desc =  mappings.mapMethodDesc(it.bsm.desc)
                            it.bsm = Handle(
                                it.bsm.tag,
                                mappings.mapType(it.bsm.owner),
                                mappings.mapMethodName(it.bsm.owner, it.bsm.name, desc),
                                desc,
                                it.bsm.isInterface
                            )
                        }
                        is MethodInsnNode -> {
                            val mapDesc =  mappings.mapMethodDesc(it.desc)

                            it.name =  mappings.mapMethodName(it.owner, it.name, mapDesc)
                            it.owner =  mappings.mapClassName(it.owner)
                            it.desc = mapDesc
                        }
                        is MultiANewArrayInsnNode -> {
                            it.desc =  mappings.mapType(it.desc)
                        }
                        is TypeInsnNode -> {
                            it.desc =  mappings.mapClassName(it.desc)
                        }
                    }
                }


                node
            }
        }


        val classloader = object : ClassLoader(this::class.java.classLoader) {
            var loaded = false
            override fun loadClass(name: String?): Class<*> {
                if (name == ToTransform::class.java.name && !loaded) {
                    loaded = true
                    val resolve = Archives.resolve(
                        ClassReader(ToTransform::class.java.name),
                        config,
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