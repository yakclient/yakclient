package dev.extframework.core.api.delegate;

public @interface Delegate {
    String value();

    String ref() default "";
}
