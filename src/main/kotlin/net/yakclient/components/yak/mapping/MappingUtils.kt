package net.yakclient.components.yak.mapping

import net.yakclient.archive.mapper.MappedArchive
import net.yakclient.archive.mapper.MappedClass
import net.yakclient.archives.extension.parameters
import net.yakclient.archives.transform.ByteCodeUtils
import net.yakclient.archives.transform.MethodSignature

public fun MappedArchive.getMappedClass(jvmName: String): MappedClass? {
    return classes.getByReal(jvmName)
}

public fun MappedArchive.mapClassName(jvmName: String): String {
    return getMappedClass(jvmName)?.fakeName ?: jvmName
}

// All expected to be in jvm class format. ie. org/example/MyClass
// Maps the
public fun MappedArchive.mapType(jvmType: String): String {
    return if (jvmType.isEmpty()) jvmType
    else if (ByteCodeUtils.isPrimitiveType(jvmType.first())) jvmType
    else if (jvmType.startsWith("[")) {
        "[" + mapType(jvmType.substring(1 until jvmType.length))
    } else {
        val mapClassName = mapClassName(jvmType.trim('L', ';'))
        "L$mapClassName;"
    }
}

public fun MappedArchive.mapMethodDesc(desc: String): String {
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

public fun MappedArchive.mapMethodSignature(cls: String, signature: String) : String {
    val (name, desc, returnType) = MethodSignature.of(signature)
    checkNotNull(returnType) {"Cannot map a method signature with a non-existent return type. Signature was '$signature'"}

    val mappedDesc = mapMethodDesc("($desc)$returnType")
    return mapMethodName(cls, name, mappedDesc) + mappedDesc
}

public fun MappedArchive.mapMethodName(cls: String, name: String, mappedDesc: String): String {
    val mappedClass = getMappedClass(cls)
    val method = mappedClass?.methods?.getByReal(name + mappedDesc)
    return method?.fakeName ?: name
}

// TODO fix, archive mapper shouldnt deal in '/'s so this shouldnt realy need to exist
public fun String.withSlashes(): String = replace('.', '/')
public fun String.withDots(): String = replace('/', '.')