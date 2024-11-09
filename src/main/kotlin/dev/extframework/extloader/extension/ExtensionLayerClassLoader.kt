package dev.extframework.extloader.extension

public class ExtensionLayerClassLoader(
    parent: ClassLoader,
) : ClassLoader(
    parent
) {
    override fun toString(): String {
        return "Extension Layer"
    }
}