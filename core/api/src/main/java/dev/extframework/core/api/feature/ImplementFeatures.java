package dev.extframework.core.api.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ImplementFeatures {
    /**
     * The feature container to implement features from.
     */
//    Class<?> value() default ;
}

