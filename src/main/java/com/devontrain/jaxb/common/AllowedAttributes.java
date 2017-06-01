package com.devontrain.jaxb.common;

@java.lang.annotation.Retention(value = java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(value = { java.lang.annotation.ElementType.FIELD })
public @interface AllowedAttributes {

    Class<?>[] value() default {};

}
