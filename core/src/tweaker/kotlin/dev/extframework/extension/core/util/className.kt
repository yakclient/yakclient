package dev.extframework.extension.core.util

public fun String.withSlashes(): String = replace('.', '/')
public fun String.withDots(): String = replace('/', '.')