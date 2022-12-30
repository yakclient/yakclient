package net.yakclient.components.yak.mapping

import net.yakclient.archive.mapper.*
import net.yakclient.archive.mapper.PrimitiveTypeIdentifier.*
import net.yakclient.archives.extension.parameters
import net.yakclient.archives.transform.ByteCodeUtils
import net.yakclient.archives.transform.MethodSignature

public fun ArchiveMapping.getMappedClass(jvmName: String): ClassMapping? {
    return classes[ClassIdentifier(jvmName, MappingType.REAL)]
}

public fun ArchiveMapping.mapClassName(jvmName: String): String {
    return getMappedClass(jvmName)?.fakeIdentifier?.name ?: jvmName
}

// All expected to be in jvm class format. ie. org/example/MyClass
// Maps the
public fun ArchiveMapping.mapType(jvmType: String): String {
    return if (jvmType.isEmpty()) jvmType
    else if (ByteCodeUtils.isPrimitiveType(jvmType.first())) jvmType
    else if (jvmType.startsWith("[")) {
        "[" + mapType(jvmType.substring(1 until jvmType.length))
    } else {
        val mapClassName = mapClassName(jvmType.trim('L', ';'))
        "L$mapClassName;"
    }
}

public fun ArchiveMapping.mapMethodDesc(desc: String): String {
    val signature = MethodSignature.of(desc)
    val parameters = parameters(signature.desc)

    check(signature.name.isBlank()) { "#mapDesc in 'net.yakclient.components.yak.mapping' is only used to map a method descriptor, not its name and descriptor! use #mapMethodSignature instead!" }

    return parameters.joinToString(
        separator = "",
        prefix = signature.name + "(",
        postfix = ")" + (signature.returnType?.let(::mapType) ?: ""),
        transform = ::mapType
    )
}

public fun ArchiveMapping.mapMethodSignature(cls: String, signature: String): String {
    val (name, desc, returnType) = MethodSignature.of(signature)
    checkNotNull(returnType) { "Cannot map a method signature with a non-existent return type. Signature was '$signature'" }
    val mappedDesc = mapMethodDesc("($desc)$returnType")
    return mapMethodName(cls, name, signature) + mappedDesc
}

// Maps a JVM type to a TypeIdentifier
public fun toTypeIdentifier(type: String): TypeIdentifier = when (type) {
    "Z" -> BOOLEAN
    "C" -> CHAR
    "B" -> BYTE
    "S" -> SHORT
    "I" -> INT
    "F" -> FLOAT
    "J" -> LONG
    "D" -> DOUBLE
    "V" -> VOID
    else -> {
        if (type.startsWith("[")) {
            val type = type.removePrefix("[")

            ArrayTypeIdentifier(toTypeIdentifier(type))
        } else if (type.startsWith("L") && type.endsWith(";")) ClassTypeIdentifier(type.removePrefix("L").removeSuffix(";"))
        else throw IllegalArgumentException("Unknown type: '$type' when trying to parse type identifier!")
    }
}

public fun ArchiveMapping.mapMethodName(cls: String, name: String, desc: String): String {
    val clsMapping = getMappedClass(cls)


    val method = clsMapping?.methods?.get(
        MethodIdentifier(
            name,
            run {
                parameters(MethodSignature.of(desc).desc)
            }.map(::toTypeIdentifier),
            MappingType.REAL
        )
    )
    return method?.fakeIdentifier?.name ?: name
}

public fun String.withSlashes(): String = replace('.', '/')
public fun String.withDots(): String = replace('/', '.')