package dev.extframework.extension.core.internal

//internal class RootMixinAgent(
//    private val app: ApplicationTarget,
//) : MixinAgent {
//    override fun transformClass(name: String, bytes: ByteArray?): ByteArray? {
//        if (bytes !=null) return bytes
//
//        return app.node.handle?.classloader?.getResourceAsStream(name.withSlashes() + ".class")?.readInputStream()
//    }
//}