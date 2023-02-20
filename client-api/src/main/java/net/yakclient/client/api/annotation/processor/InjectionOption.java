package net.yakclient.client.api.annotation.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
public @interface InjectionOption {
    public static final String DEFAULT_NAME = "<self>";

    String value() default DEFAULT_NAME; // name of the option
}
