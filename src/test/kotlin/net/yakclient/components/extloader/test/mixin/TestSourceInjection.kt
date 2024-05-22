package net.yakclient.components.extloader.test.mixin

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.MappingNodeContainerImpl
import net.yakclient.archive.mapper.MappingValueContainerImpl
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.extension.Method
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.archives.mixin.SourceInjectionPoint
import net.yakclient.archives.mixin.SourceInjectors
import net.yakclient.archives.transform.ProvidedInstructionReader
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.loader.SourceProvider
import net.yakclient.client.api.AFTER_BEGIN
import net.yakclient.client.api.InjectionContinuation
import net.yakclient.client.api.annotation.SourceInjection
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider
import net.yakclient.components.extloader.environment.registerMixinPoints
import net.yakclient.components.extloader.environment.registerMixins
import net.yakclient.components.extloader.extension.ProcessedMixinContext
import net.yakclient.components.extloader.extension.processClassForMixinContexts
import net.yakclient.components.extloader.mixin.SourceInjectionProvider
import net.yakclient.`object`.ObjectContainerImpl
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import runBootBlocking
import java.net.URI
import java.nio.ByteBuffer
import kotlin.test.Test

class TestSourceInjection {
    private fun Class<*>.classNode(): ClassNode {
        val n = ClassNode()
        ClassReader(this.name).accept(n, 0)
        return n
    }

    private fun Class<*>.applyTransformations(config: TransformerConfig): Class<*> {
        val reader = ClassReader(this.name)

        val bytes = Archives.resolve(reader, config)

        val loader = IntegratedLoader("${this.name} mixin Loader", sourceProvider = object : SourceProvider {
            override val packages: Set<String> = setOf(name.substring(name.lastIndexOf(".")))

            override fun findSource(name: String): ByteBuffer? {
                return if (name == this@applyTransformations.name) return ByteBuffer.wrap(bytes)
                else null
            }

        }, parent = TestSourceInjection::class.java.classLoader)

        return loader.loadClass(name)
    }

    private fun <A : Annotation, T : MixinInjection.InjectionData> ProcessedMixinContext<A, T>.parseAndApply(
        mappingContext: MixinInjectionProvider.MappingContext,
        reference: ArchiveReference,
    ): Job<TransformerConfig.Mutable> = job {
        provider.get().apply(
            provider.parseData(
                this@parseAndApply.context,
                mappingContext,
                reference
            )().merge()
        )
    }

    private abstract class MixinClass {
        fun inject(capture: String, continuation: InjectionContinuation): InjectionContinuation.Result {
            println("HA! Captured you: '$capture'")

            return continuation.returnEarly(10)
        }
    }

    @Test
    fun `Test basic source injection with given data context`() {
        val continuationResultInternalName = InjectionContinuation.Result::class.java.name.replace('.', '/')
        val continuationInternalName = InjectionContinuation::class.java.name.replace('.', '/')
        val data = SourceInjectionProvider.RichSourceInjectionData(
            "test",
            "net/yakclient/components/extloader/test/mixin/TestDestination",
            "net/yakclient/components/extloader/test/mixin/TestSourceInjection\$MixinClass",
            run {
                ProvidedInstructionReader(
                    ClassNode().also {
                        ClassReader(MixinClass::class.java.name).accept(
                            it,
                            0
                        )
                    }.methods.find { it.name == "inject" }!!.instructions
                )
            },
            Method("inject(Ljava/lang/String;L$continuationInternalName;)L$continuationResultInternalName;"),
            Method("destMethod(Ljava/lang/String;)I"),
            SourceInjectors.AFTER_BEGIN,
            false
        )

        val config = SourceInjectionProvider().get().apply(data)

        val newClass = TestDestination::class.java.applyTransformations(config)
        val obj = newClass.getConstructor().also { it.trySetAccessible() }.newInstance()
        println(newClass.getDeclaredMethod("destMethod", String::class.java).apply { trySetAccessible() }
            .invoke(obj, "Test"))
    }

    private object StaticMixinClass {
        @JvmStatic
        fun inject(a: Long, capture: String, continuation: InjectionContinuation): InjectionContinuation.Result {
            println("HA! Captured you: '$capture'")

            return continuation.returnEarly(10)
        }
    }

    @Test
    fun `Test static source injection with given data context`() {
        val continuationResultInternalName = InjectionContinuation.Result::class.java.name.replace('.', '/')
        val continuationInternalName = InjectionContinuation::class.java.name.replace('.', '/')
        val data = SourceInjectionProvider.RichSourceInjectionData(
            "test",
            "net/yakclient/components/extloader/test/mixin/TestDestination",
            "net/yakclient/components/extloader/test/mixin/TestSourceInjection\$StaticMixinClass",
            run {
                ProvidedInstructionReader(
                    ClassNode().also {
                        ClassReader(StaticMixinClass::class.java.name).accept(
                            it,
                            0
                        )
                    }.methods.find { it.name == "inject" }!!.instructions
                )
            },
            Method("inject(JLjava/lang/String;L$continuationInternalName;)L$continuationResultInternalName;"),
            Method("staticDestMethod(JLjava/lang/String;)I"),
            SourceInjectors.AFTER_BEGIN,
            true
        )

        val config = SourceInjectionProvider().get().apply(data)
        val newClass = TestDestination::class.java.applyTransformations(config)
        val r = newClass.getDeclaredMethod("staticDestMethod", Long::class.java, String::class.java)
            .apply { trySetAccessible() }
            .invoke(null, 0L, "Test")

        check(r == 10) { "Injection did not apply properly, return value should have been 10." }
    }

    public abstract class AnnotatedMixinClass {
        @SourceInjection(
            point = AFTER_BEGIN,
            methodTo = "destMethod(Ljava/lang/String;)I"
        )
        fun inject(capture: String, continuation: InjectionContinuation): InjectionContinuation.Result {
            println("HA! Captured you: '$capture'")

            return continuation.returnEarly(6)
        }
    }

    @Test
    fun `Test parsing source injections injects correctly`() {
        val provider = SourceInjectionProvider()

        val contexts = processClassForMixinContexts(
            AnnotatedMixinClass::class.java.classNode(),
            TestDestination::class.java.classNode(),
            ObjectContainerImpl<MixinInjectionProvider<*, *>>().also {
                it.register("source", provider)
            }
        )
        runBootBlocking {
            val config = contexts.fold(TransformerConfig.Mutable()) { acc, it ->
                val config = it.parseAndApply(
                    MixinInjectionProvider.MappingContext(
                        HashMap(),
                        ArchiveMapping(setOf(), MappingValueContainerImpl(mapOf()), MappingNodeContainerImpl(setOf())),
                        "none",
                        ExtLoaderEnvironment().also {
                            it += ApplicationMappingTarget("")
                            it += MutableObjectContainerAttribute(
                                injectionPointsAttrKey,
                                ObjectContainerImpl<SourceInjectionPoint>()
                            ).apply {
                                registerMixinPoints()
                            }
                            it += MutableObjectContainerAttribute<MixinInjectionProvider<*,*>>(mixinTypesAttrKey).apply {
                                registerMixins()
                            }
                        },
                        "test-ext"
                    ),
                    object : ArchiveReference {
                        override val reader: ArchiveReference.Reader
                            get() = TODO("Not yet implemented")
                        override val isClosed: Boolean
                            get() = TODO("Not yet implemented")
                        override val location: URI
                            get() = TODO("Not yet implemented")
                        override val modified: Boolean
                            get() = TODO("Not yet implemented")
                        override val name: String = "An archive reference for testing"
                        override val writer: ArchiveReference.Writer
                            get() = TODO("Not yet implemented")

                        override fun close() {
                            TODO("Not yet implemented")
                        }
                    }
                )().merge()
                acc.transformClass {
                    config.ct(it)
                }
                acc.transformMethod {
                    config.mt(it)
                }
                acc.transformField {
                    config.ft(it)
                }
                acc
            }

            val newClass = TestDestination::class.java.applyTransformations(config)
            val r = newClass.getDeclaredMethod("destMethod", String::class.java)
                .apply { trySetAccessible() }
                .invoke(newClass.getConstructor().newInstance(), "Test")

            check(r == 6) { "Injection did not apply properly, return value should have been 6." }
        }
    }
}