package dev.extframework.extension.core.minecraft

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.common.util.resolve
import dev.extframework.extension.core.annotation.AnnotationProcessor
import dev.extframework.extension.core.minecraft.environment.ApplicationMappingTarget
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.remappersAttrKey
import dev.extframework.extension.core.minecraft.internal.MojangMappingProvider
import dev.extframework.extension.core.minecraft.internal.RootRemapper
import dev.extframework.extension.core.minecraft.internal.SourceInjectionRemapper
import dev.extframework.extension.core.minecraft.partition.MinecraftPartitionLoader
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.tweaker.EnvironmentTweaker

public class MinecraftCoreTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        environment += MutableObjectSetAttribute(mappingProvidersAttrKey)
        environment[mappingProvidersAttrKey].extract().add(MojangMappingProvider(
            environment[wrkDirAttrKey].extract().value resolve "mappings"
        ))

        val partitionContainer = environment[partitionLoadersAttrKey].extract().container
        partitionContainer.register("target", MinecraftPartitionLoader(environment))

        val remappers = MutableObjectSetAttribute(remappersAttrKey)
        environment += remappers
        remappers.add(RootRemapper())
        remappers.add(SourceInjectionRemapper(environment[AnnotationProcessor].extract()))

        environment[ApplicationMappingTarget].extract()
    }
}