package net.yakclient.client.api.annotation;

import net.yakclient.client.api.InjectionPriorities;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static net.yakclient.client.api.annotation.InjectionDefaults.SELF_REF;
import static net.yakclient.client.api.annotation.InjectionDefaults.SELF_REF_ACCESS;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodInjection {
    public String methodFrom() default SELF_REF;

    public int access() default SELF_REF_ACCESS;

    public String name() default SELF_REF;

    public String descriptor() default SELF_REF;

    public String signature() default SELF_REF;

    public String exceptions() default SELF_REF;

    public int priority() default InjectionPriorities.DEFAULT;
}
