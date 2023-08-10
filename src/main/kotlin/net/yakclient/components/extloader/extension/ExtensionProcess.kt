package net.yakclient.components.extloader.extension

import net.yakclient.archive.mapper.transform.ClassInheritanceTree
import net.yakclient.archive.mapper.transform.MappingDirection
import net.yakclient.archive.mapper.transform.mapClassName
import net.yakclient.archives.mixin.MixinInjection
import net.yakclient.boot.container.ContainerProcess
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.common.util.immutableLateInit
import net.yakclient.components.extloader.extension.archive.ExtensionArchiveHandle
import net.yakclient.components.extloader.util.withDots
import net.yakclient.components.extloader.util.withSlashes
import net.yakclient.internal.api.InternalRegistry
import net.yakclient.internal.api.extension.ExtensionMixin
import net.yakclient.internal.api.extension.ExtensionVersionPartition
import net.yakclient.internal.api.extension.archive.ExtensionArchiveReference
import net.yakclient.internal.api.mixin.MixinInjectionProvider
import net.yakclient.minecraft.bootstrapper.MixinMetadata

public data class ExtensionProcess(
    val ref: ExtensionReference,
    private val context: ExtensionContext
) : ContainerProcess {
    override val archive: ExtensionArchiveHandle
        get() = ref.archive

    override fun start(): Unit = ref.extension.init()
}

public data class ExtensionReference(
        private val archiveReference: ExtensionArchiveReference,
        private val inheritanceTree: ClassInheritanceTree,
        private val lazyLoader: (minecraft: MinecraftLinker) -> Pair<Extension, ExtensionArchiveHandle>,
) {
    public var extension: Extension by immutableLateInit()
    public var archive: ExtensionArchiveHandle by immutableLateInit()

    public fun injectMixins(register: (to: String, metadata: MixinMetadata<*>) -> Unit) {
        // Eventually, take out main partition. There should be no mixins in the main partition.
        (archiveReference.enabledPartitions + archiveReference.mainPartition)
                .flatMap { v -> v.mixins.map { v to it } }
                .groupBy { it.first.mappings }
                .flatMap { (ref, mixins: List<Pair<ExtensionVersionPartition, ExtensionMixin>>) ->
                    val mapper = InternalRegistry.extensionMappingContainer.get(ref.type)?.forIdentifier(ref.identifier)
                            ?: throw IllegalArgumentException("Failed to find mapping type: '${ref.type}', options are: '${InternalRegistry.extensionMappingContainer.objects().keys}")

                    mixins.map { it.second to mapper }
                }.forEach { (mixin, mappings) ->
                    mixin.injections.forEach {
                        val provider = InternalRegistry.mixinTypeContainer.get(it.type)
                                ?: throw IllegalArgumentException("Unknown mixin type: '${it.type}' in mixin class: '${mixin.classname}', options are: '${InternalRegistry.mixinTypeContainer.objects().keys}")

                        val to = (mappings.mapClassName(mixin.destination.withSlashes(), MappingDirection.TO_FAKE)?.withDots()
                                ?: mixin.destination.withSlashes())
                        val mixinMetadata = MixinMetadata(
                                provider.parseData(
                                        it.options,
                                        MixinInjectionProvider.MappingContext(inheritanceTree, mappings),
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