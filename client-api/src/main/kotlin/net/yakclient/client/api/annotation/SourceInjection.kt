package net.yakclient.client.api.annotation

import net.yakclient.client.api.annotation.processor.InjectionMetadata
import net.yakclient.client.api.annotation.processor.InjectionOption
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption

@InjectionMetadata("source")
public annotation class SourceInjection(
    @InjectionOption("point")
    public val point: String, // The source injection point to use (where in the method)

    @InjectionOption("self")
    public val from: String, // The class that this injection is from
    @InjectionOption("to")
    public val to: String, // the class that the injection is going to

    @InjectionOption("methodFrom")
    public val methodFrom: String, // the method that this injection is from (the method that is annotated with this)
    @InjectionOption("methodTo")
    public val methodTo: String, // the method that the sources will be injected into

    @InjectionPriorityOption
    public val priority: Int
)



