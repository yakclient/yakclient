package net.yakclient.plugins.yakclient.extension.yak
//
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.module.kotlin.KotlinModule
//import com.fasterxml.jackson.module.kotlin.readValue
//import net.yakclient.plugins.yakclient.extension.yak.artifact.YakExtDescriptor
//import net.yakclient.plugins.yakclient.store.DataAccess
//import net.yakclient.common.util.make
//import java.io.File
//import java.nio.file.Path
//import net.yakclient.common.util.resolve
//import net.yakclient.common.util.resource.LocalResource
//import java.io.FileOutputStream
//import java.nio.channels.Channels
//import java.nio.file.Files
//import java.util.logging.Level
//import java.util.logging.Logger
//import kotlin.io.path.exists
//import kotlin.io.path.writeBytes
//
//internal class YakExtensionDataAccess(
//    private val path: Path,
//) : DataAccess<DescriptorKey, YakExtensionData> {
//    private val logger = Logger.getLogger(this::class.simpleName)
//    private val mapper = ObjectMapper().registerModule(KotlinModule())
//
//    private fun createJarName(desc: YakExtDescriptor) : String = "${desc.artifactId}-${desc.version}.jar"
//    private fun createErmName(desc: YakExtDescriptor) : String = "${desc.artifactId}-${desc.version}.erm.json"
//
//    override fun read(key: DescriptorKey): YakExtensionData? {
//        val (_, jarPath, ermPath) = verifyPaths(key)
//
//        if (!ermPath.exists()) return null
//
//        val erm = mapper.readValue<YakErm>(ermPath.toFile())
//
//        val children = erm.extensionDependencies.map { DescriptorKey(YakExtDescriptor.parseDescriptor(it)!!) }
//        val dependencies = erm.dependencies.map {  }
//        val resource = if (jarPath.exists()) LocalResource(jarPath.toUri()) else null
//
////        return YakExtensionData(
////            key,
////            children,
////            resource,
////            erm
////        )
//        TODO()
//    }
//
//    private fun verifyPaths(key: DescriptorKey) : Triple<YakExtDescriptor, Path, Path> {
//        val desc = key.desc as? YakExtDescriptor ?: throw IllegalArgumentException("Invalid descriptor: '${key.desc::class.qualifiedName}'.")
//
//        val extBasePath = path resolve desc.groupId.replace(".", File.separator)
//        val jarPath = extBasePath resolve createJarName(desc)
//        val ermPath = extBasePath resolve createErmName(desc)
//
//        return Triple(desc, jarPath, ermPath)
//    }
//
//    override fun write(key: DescriptorKey, value: YakExtensionData) {
//        val (desc, jarPath, ermPath) = verifyPaths(key)
//
//        if (!Files.exists(jarPath) && value.archive != null) {
//            logger.log(Level.INFO, "Downloading Yak Extension: '$desc'.")
//
//            Channels.newChannel(value.archive.open()).use { cin ->
//                jarPath.make()
//                FileOutputStream(jarPath.toFile()).use { fout ->
//                    fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
//                }
//            }
//        }
//
//        ermPath.make()
//        ermPath.writeBytes(mapper.writeValueAsBytes(value.erm))
//    }
//}