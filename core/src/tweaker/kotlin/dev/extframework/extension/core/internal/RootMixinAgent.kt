package dev.extframework.extension.core.internal

import dev.extframework.common.util.readInputStream
import dev.extframework.extension.core.mixin.MixinAgent
import dev.extframework.extension.core.util.withSlashes
import dev.extframework.internal.api.target.ApplicationTarget

//internal class RootMixinAgent(
//    private val app: ApplicationTarget,
//) : MixinAgent {
//    override fun transformClass(name: String, bytes: ByteArray?): ByteArray? {
//        if (bytes !=null) return bytes
//
//        return app.node.handle?.classloader?.getResourceAsStream(name.withSlashes() + ".class")?.readInputStream()
//    }
//}