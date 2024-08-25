package dev.extframework.extension.core.minecraft.mixin

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.transform.ClassInheritanceTree
import dev.extframework.archives.mixin.MixinInjection
import dev.extframework.extension.core.minecraft.environment.ApplicationMappingTarget
import dev.extframework.extension.core.mixin.MixinInjectionProvider
import dev.extframework.extension.core.mixin.MixinInjectionProvider.InjectionContext
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.extract

//public interface MappingInjectionProvider<A : Annotation, T : MixinInjection.InjectionData> :
//    MixinInjectionProvider<A, T> {
//
//    public fun mapContext(
//        ctx: InjectionContext<A>,
//        mappingCtx: Context
//    ): Job<InjectionContext<A>>
//
//    public fun mapData(
//        data: T,
//        cxt: Context
//    ): Job<T>
//
//    public fun parseDataAndMap(
//        context: InjectionContext<A>,
//        mappingCtx: Context,
//    ) : Job<T> = job {
//        val mappedContext = mapContext(context, mappingCtx)().merge()
//
//        val unmappedData = parseData(
//            mappedContext
//        )().merge()
//
//        mapData(unmappedData, mappingCtx)().merge()
//    }
//
//    public data class Context(
//        val tree: ClassInheritanceTree,
//        val mappings: ArchiveMapping,
//        val fromNS: String,
//        val environment: ExtensionEnvironment,
//    ) {
//        val toNS: String = environment[ApplicationMappingTarget].extract().namespace
//    }
//}