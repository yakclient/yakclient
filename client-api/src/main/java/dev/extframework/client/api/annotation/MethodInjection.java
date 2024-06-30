package dev.extframework.client.api.annotation;

import dev.extframework.client.api.InjectionPriorities;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodInjection {
    public String methodFrom() default InjectionDefaults.SELF_REF;

    public int access() default InjectionDefaults.SELF_REF_ACCESS;

    public String name() default InjectionDefaults.SELF_REF;

    public String descriptor() default InjectionDefaults.SELF_REF;

    public String signature() default InjectionDefaults.SELF_REF;

    public String exceptions() default InjectionDefaults.SELF_REF;

    public int priority() default InjectionPriorities.DEFAULT;
}
