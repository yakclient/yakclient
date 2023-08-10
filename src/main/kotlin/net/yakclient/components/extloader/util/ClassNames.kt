package net.yakclient.components.extloader.util

public fun String.withSlashes(): String = replace('.', '/')
public fun String.withDots(): String = replace('/', '.')