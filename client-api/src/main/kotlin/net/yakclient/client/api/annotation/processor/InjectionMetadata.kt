package net.yakclient.client.api.annotation.processor

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class InjectionMetadata(
    public val type: String
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
public annotation class InjectionOption(
    public val name: String,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
public annotation class InjectionPriorityOption
