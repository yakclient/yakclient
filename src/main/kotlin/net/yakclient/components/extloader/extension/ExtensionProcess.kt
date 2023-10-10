package net.yakclient.components.extloader.extension

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.mapClassName
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.boot.container.ContainerProcess
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.common.util.immutableLateInit
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.extloader.api.target.MixinTransaction
import net.yakclient.components.extloader.util.withDots
import net.yakclient.components.extloader.util.withSlashes
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.environment.mixinTypesAttrKey
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.mixin.MixinInjectionProvider

public data class ExtensionProcess(
    val ref: ExtensionReference,
    private val context: ExtensionContext
) : ContainerProcess {
    override val archive: ExtensionArchiveHandle
        get() = ref.archive

    override fun start(): Unit = ref.extension.init()
}

public data class ExtensionReference(
    private val environment: ExtLoaderEnvironment,
    private val archiveReference: ExtensionArchiveReference,
    private val inheritanceTree: ClassInheritanceTree,
    private val mappings: ArchiveMapping,
    private val lazyLoader: (minecraft: MinecraftLinker) -> Pair<Extension, ExtensionArchiveHandle>,
) {
    public var extension: Extension by immutableLateInit()
    public var archive: ExtensionArchiveHandle by immutableLateInit()

    public fun injectMixins(register: (to: String, metadata: MixinTransaction.Metadata<*>) -> Unit) {
        // There should be no mixins in the main partition
        (archiveReference.enabledPartitions)
            .flatMap { v -> v.mixins.map { it } }
//                .flatMap {  mixins: List<Pair<ExtensionVersionPartition, ExtensionMixin>> ->
//                    val mapper = InternalRegistry.extensionMappingContainer.get(ref.type)?.forIdentifier(mcVersion)
//                            ?: throw IllegalArgumentException("Failed to find mapping type: '${ref.type}', options are: '${InternalRegistry.extensionMappingContainer.objects().keys}")

//                        mixins.map { it.second to mapper }
            .forEach { mixin ->
                mixin.injections.forEach {
                    val provider: MixinInjectionProvider<*> = environment[mixinTypesAttrKey]?.get(it.type)
                        ?: throw IllegalArgumentException("Unknown mixin type: '${it.type}' in mixin class: '${mixin.classname}', options are: '${environment[mixinTypesAttrKey]?.objects()?.keys ?: listOf()}")

                    val to =
                        (mappings.mapClassName(mixin.destination.withSlashes(), MappingDirection.TO_FAKE)?.withDots()
                            ?: mixin.destination.withSlashes())
                    val mixinMetadata = MixinTransaction.Metadata(
                        provider.parseData(
                            it.options,
                            MixinInjectionProvider.MappingContext(inheritanceTree, mappings, environment),
                            archiveReference
                        ),
                        provider.get() as MixinInjection<MixinInjection.InjectionData>
                    )
                    register(to, mixinMetadata)
                }
            }
    }

    public fun setup(handle: MinecraftLinker) {
        val (e, a) = lazyLoader(handle)
        extension = e
        archive = a
    }
}