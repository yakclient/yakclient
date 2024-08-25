package dev.extframework.extension.core.util

import org.objectweb.asm.Opcodes

internal fun currentCFVersion(): Int {
    val specV = System.getProperty("java.specification.version")
    return when (specV) {
        "8" -> Opcodes.V1_8
        "9" -> Opcodes.V9
        "10" -> Opcodes.V10
        "11" -> Opcodes.V11
        "12" -> Opcodes.V12
        "13" -> Opcodes.V13
        "14" -> Opcodes.V14
        "15" -> Opcodes.V15
        "16" -> Opcodes.V16
        "17" -> Opcodes.V17
        "18" -> Opcodes.V18
        "19" -> Opcodes.V19
        "20" -> Opcodes.V20
        "21" -> Opcodes.V21
        "22" -> Opcodes.V22
        else -> throw IllegalArgumentException("Unknown java specification version (java.specification.version): $specV")
    }
}