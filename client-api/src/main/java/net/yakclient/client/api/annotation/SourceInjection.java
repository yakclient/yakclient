package net.yakclient.client.api.annotation;

import net.yakclient.client.api.InjectionPriorities;
import net.yakclient.client.api.annotation.processor.InjectionMetadata;
import net.yakclient.client.api.annotation.processor.InjectionOption;
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@InjectionMetadata("source")
public @interface SourceInjection {
    @InjectionOption("point")
    String point();

    @InjectionOption("methodTo")
    String methodTo();

    @InjectionPriorityOption
    int priority() default InjectionPriorities.DEFAULT;
}