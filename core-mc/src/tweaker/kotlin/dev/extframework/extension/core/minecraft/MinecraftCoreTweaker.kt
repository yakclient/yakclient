package dev.extframework.extension.core.minecraft

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.common.util.resolve
import dev.extframework.extension.core.minecraft.environment.MappingNamespace
import dev.extframework.extension.core.minecraft.environment.mappingProvidersAttrKey
import dev.extframework.extension.core.minecraft.environment.mappingTargetAttrKey
import dev.extframework.extension.core.minecraft.environment.remappersAttrKey
import dev.extframework.extension.core.minecraft.internal.MinecraftApp
import dev.extframework.extension.core.minecraft.internal.MojangMappingProvider
import dev.extframework.extension.core.minecraft.partition.MinecraftPartitionLoader
import dev.extframework.extension.core.target.InstrumentedApplicationTarget
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.target.ApplicationTarget
import dev.extframework.internal.api.tweaker.EnvironmentTweaker

public class MinecraftCoreTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        // TODO this is sloppy, there should be an actual way to set the mapping target from non
        //    extension contexts (such as the client)
        val sysMappingTarget = System.getProperty("mapping.target")
        if (sysMappingTarget != null) {
            environment.addUnless(
                ValueAttribute(
                    MappingNamespace.parse(sysMappingTarget),
                    mappingTargetAttrKey
                )
            )
        } else environment.addUnless(
            ValueAttribute(
                MojangMappingProvider.OBF_TYPE,
                mappingTargetAttrKey
            )
        )

        // Mapping providers
        environment += MutableObjectSetAttribute(mappingProvidersAttrKey)
        environment[mappingProvidersAttrKey].extract().add(
            MojangMappingProvider(
                environment[wrkDirAttrKey].extract().value resolve "mappings"
            )
        )

        // Minecraft app
        environment += MinecraftApp(
            (environment[ApplicationTarget].extract() as InstrumentedApplicationTarget).delegate,
            environment
        )

        // Minecraft partition
        val partitionContainer = environment[partitionLoadersAttrKey].extract().container
        partitionContainer.register("target", MinecraftPartitionLoader(environment))

        // Remappers
        val remappers = MutableObjectSetAttribute(remappersAttrKey)
        environment += remappers
    }
}