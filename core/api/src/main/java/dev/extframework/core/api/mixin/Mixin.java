package dev.extframework.core.api.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mixin {
    Class<?> value(); // the destination this mixin

    // TODO it would be nice if this could ? extends MixinSubSystem however they are in seperate modules and
    //    I dont want to combine them anyways...
    // Object.class will change to the default subsystem
    Class<?> subsystem() default Object.class;
}
