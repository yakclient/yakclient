package net.yakclient.client.api.annotation

import net.yakclient.client.api.annotation.processor.InjectionMetadata
import net.yakclient.client.api.annotation.processor.InjectionOption
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption

// Differs from source injection, source injection requires a method with its signature
// to already exist, and just updates the sources. This will inject a whole new method.
@InjectionMetadata("method")
public annotation class MethodInjection(
    @InjectionOption("self")
    public val from: String, // Class this injection is from
    @InjectionOption("to")
    public val to: String, // Class this injection is to
    @InjectionOption("methodFrom")
    public val methodFrom: String, // Method this injection is from

    @InjectionOption("access")
    public val access: Int, // Access of the created method
    @InjectionOption("name")
    public val name: String, // Name of the created method
    @InjectionOption("description")
    public val description: String, // Description of the created method
    @InjectionOption("signature")
    public val signature: String, // signature of the created method
    @InjectionOption("exceptions")
    public val exceptions: String, // Exceptions of the created method, Delimiter split by ','

    @InjectionPriorityOption
    public val priority: Int
)
