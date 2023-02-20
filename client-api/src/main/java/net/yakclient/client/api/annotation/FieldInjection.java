package net.yakclient.client.api.annotation;

import net.yakclient.client.api.annotation.processor.InjectionMetadata;
import net.yakclient.client.api.annotation.processor.InjectionOption;
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@InjectionMetadata("field")
public @interface FieldInjection {
    @InjectionOption("name")
    String name(); // The field name

    @InjectionOption("access")
    String access(); // The field access

    @InjectionOption("type")
    int type(); // The field type

    @InjectionOption("signature")
    String signature(); // The field signature

    @InjectionPriorityOption
    int priority() default 0;
}
