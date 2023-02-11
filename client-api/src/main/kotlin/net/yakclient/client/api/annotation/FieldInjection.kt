package net.yakclient.client.api.annotation

import net.yakclient.client.api.annotation.processor.InjectionMetadata
import net.yakclient.client.api.annotation.processor.InjectionOption
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption

// Injects a field into the given class

@InjectionMetadata("field")
public annotation class FieldInjection(
    @InjectionOption("name")
    public val name: String, // The field name
    @InjectionOption("access")
    public val access: String, // The field access
    @InjectionOption("type")
    public val type: Int, // The field type
    @InjectionOption("signature")
    public val signature: String, // The field signature
    @InjectionOption("value")
    public val value: String, // Initial value of the field, must be a primitive type

    @InjectionPriorityOption
    public val priority: Int
)
