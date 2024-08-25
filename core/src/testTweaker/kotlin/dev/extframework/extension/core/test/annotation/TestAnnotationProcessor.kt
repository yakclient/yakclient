package dev.extframework.extension.core.test.annotation

import dev.extframework.extension.core.annotation.AnnotationTarget
import dev.extframework.extension.core.internal.AnnotationProcessorImpl
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import kotlin.test.assertTrue

class TestAnnotationProcessor {
    @Test
    fun `Test process this class`() {
        val processor = AnnotationProcessorImpl()

        val node = ClassNode().apply {
            ClassReader(this@TestAnnotationProcessor::class.java.name).accept(this, 0)
        }
        val processed = processor.process(
            node,
            Test::class.java
        )

        assertTrue(processed.size == 1)
    }

    @Retention(AnnotationRetention.RUNTIME)
    annotation class MyAnnotation

    @MyAnnotation
    class BasicClass @MyAnnotation constructor() {
        @field:MyAnnotation
        val field: String = ""

        @MyAnnotation
        fun method() { }

        fun methodWithParams(
            @MyAnnotation string: String,
        ) {  }
    }

    @Test
    fun `Test basic class`() {
        val processor = AnnotationProcessorImpl()

        val node = ClassNode().apply {
            ClassReader(BasicClass::class.java.name).accept(this, 0)
        }

        val processed = processor.process(
            node,
            MyAnnotation::class.java
        )

        assertTrue(processed.filter {
            it.target.elementType == AnnotationTarget.ElementType.CLASS
        }.size == 1)
        assertTrue(processed.filter {
            it.target.elementType == AnnotationTarget.ElementType.METHOD
        }.size == 2)
        assertTrue(processed.filter {
            it.target.elementType == AnnotationTarget.ElementType.FIELD
        }.size == 1)
        assertTrue(processed.filter {
            it.target.elementType == AnnotationTarget.ElementType.PARAMETER
        }.size == 1)
    }
}