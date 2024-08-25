package dev.extframework.core.api.mixin;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SourceInjection {
    String point();

    String methodTo();

    int priority() default InjectionPriorities.DEFAULT;
}