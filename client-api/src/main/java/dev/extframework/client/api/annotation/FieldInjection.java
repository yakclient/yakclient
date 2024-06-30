package dev.extframework.client.api.annotation;

import dev.extframework.client.api.InjectionPriorities;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static dev.extframework.client.api.annotation.InjectionDefaults.SELF_REF;
import static dev.extframework.client.api.annotation.InjectionDefaults.SELF_REF_ACCESS;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldInjection {
    String name() default SELF_REF; // The field name

    int access() default SELF_REF_ACCESS; // The field access

    String type() default SELF_REF; // The field type

    String signature() default SELF_REF; // The field signature

    int priority() default InjectionPriorities.DEFAULT;
}