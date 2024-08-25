package dev.extframework.extension.core

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.archives.mixin.SourceInjectors
import dev.extframework.extension.core.environment.delegationProvidersAttrKey
import dev.extframework.extension.core.environment.injectionPointsAttrKey
import dev.extframework.extension.core.environment.mixinAgentsAttrKey
import dev.extframework.extension.core.environment.mixinTypesAttrKey
import dev.extframework.extension.core.feature.delegate.FeatureDelegationProvider
import dev.extframework.extension.core.internal.AnnotationProcessorImpl
import dev.extframework.extension.core.internal.DelegationImpl
import dev.extframework.extension.core.internal.InstrumentedAppImpl
import dev.extframework.extension.core.mixin.*
import dev.extframework.extension.core.partition.FeaturePartitionLoader
import dev.extframework.extension.core.partition.MainPartitionLoader
import dev.extframework.extension.core.target.TargetLinker
import dev.extframework.extension.core.target.TargetLinkerResolver
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.extension.ExtensionResolver
import dev.extframework.internal.api.target.ApplicationTarget
import dev.extframework.internal.api.tweaker.EnvironmentTweaker

public class CoreTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        // Mixin
        val injectionPoints = MutableObjectContainerAttribute(injectionPointsAttrKey)
        injectionPoints.container.register("after-begin", SourceInjectors.AFTER_BEGIN)
        injectionPoints.container.register("before-end", SourceInjectors.BEFORE_END)
        injectionPoints.container.register("before-invoke", SourceInjectors.BEFORE_INVOKE)
        injectionPoints.container.register("before-return", SourceInjectors.BEFORE_RETURN)
        injectionPoints.container.register("overwrite", SourceInjectors.OVERWRITE)
        environment += injectionPoints

        val mixinTypes = MutableObjectContainerAttribute(mixinTypesAttrKey)
        environment += mixinTypes
        mixinTypes.container.register("source", SourceInjectionProvider(injectionPoints.container))
        mixinTypes.container.register("field", FieldInjectionProvider())
        mixinTypes.container.register("method", MethodInjectionProvider())

        val mixinAgents = MutableObjectSetAttribute(mixinAgentsAttrKey)
        mixinAgents.add(DefaultMixinSubsystem(environment))
        environment += mixinAgents

        // Target linker/resolver
        val linker = TargetLinker()

        environment += InstrumentedAppImpl(environment[ApplicationTarget].extract(), linker, mixinAgents)

        linker.target = environment[ApplicationTarget].extract()
        environment += linker

        val targetLinkerResolver = TargetLinkerResolver(linker)
        environment += targetLinkerResolver
        environment.archiveGraph.registerResolver(targetLinkerResolver)

        // Partition loaders
        val partitionContainer = environment[partitionLoadersAttrKey].extract().container
        partitionContainer.register(MainPartitionLoader.TYPE, MainPartitionLoader(environment))
        partitionContainer.register(
            FeaturePartitionLoader.TYPE,
            FeaturePartitionLoader(environment[ExtensionResolver].extract().partitionResolver)
        )

        // Extension init
        environment += ExtensionInitRunner(environment[mixinAgentsAttrKey].extract().filterIsInstance<MixinSubsystem>(), linker)

        // Annotation processing
        environment += AnnotationProcessorImpl()

        // Delegation
        environment += MutableObjectSetAttribute(delegationProvidersAttrKey)
        val delegationProviders = environment[delegationProvidersAttrKey].extract()

        environment += DelegationImpl(delegationProviders)

        delegationProviders.add(FeatureDelegationProvider())

    }
}