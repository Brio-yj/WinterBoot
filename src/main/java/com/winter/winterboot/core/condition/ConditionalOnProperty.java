package com.winter.winterboot.core.condition;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ConditionalOnProperty {
    String prefix() default "";
    String name();
    String havingValue() default "";
    boolean matchIfMissing() default false;
}
