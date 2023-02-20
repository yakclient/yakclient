package net.yakclient.client.api.annotation;

import net.yakclient.client.api.annotation.processor.InjectionMetadata;
import net.yakclient.client.api.annotation.processor.InjectionOption;
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodInjection {
    @InjectionOption("self")
    public String from();

    @InjectionOption("to")
    public String to();

    @InjectionOption("methodFrom")
    public String methodFrom();

    @InjectionOption("access")
    public int access();

    @InjectionOption("name")
    public String name();

    @InjectionOption("description")
    public String description();

    @InjectionOption("signature")
    public String signature();

    @InjectionOption("exceptions")
    public String exceptions();

    @InjectionPriorityOption
    public int priority();
}
