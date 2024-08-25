package dev.extframework.components.extloader.extension

public class ExtensionLayerClassLoader(
    parent: ClassLoader,
) : ClassLoader(
    parent
) {
    override fun getName(): String {
        return "Extension Layer"
    }
}