package dev.extframework.components.extloader.extension

//public class ExtensionDataAccess(
//    private val path: Path,
//) : DataAccess<ExtensionDescriptor, ExtensionRuntimeModel> {
//    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
//
//    private fun createPath(descriptor: ExtensionDescriptor) : Path =  path resolve descriptor.group.replace(
//        '.',
//        File.separatorChar
//    ) resolve descriptor.artifact resolve descriptor.version resolve "${descriptor.artifact}-${descriptor.version}-erm.json"
//
//    override fun read(key: ExtensionDescriptor): ExtensionRuntimeModel? {
//        val path = createPath(key)
//        if (!Files.exists(path)) return null
//
//        return mapper.readValue<ExtensionRuntimeModel>(path.toFile())
//    }
//
//    override fun write(key: ExtensionDescriptor, value: ExtensionRuntimeModel) {
//        val path = createPath(key)
//        if (!Files.exists(path)) path.make()
//
//        path.writeBytes(mapper.writeValueAsBytes(value))
//    }
//}