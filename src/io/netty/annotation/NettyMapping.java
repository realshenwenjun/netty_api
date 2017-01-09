package io.netty.annotation;

import java.lang.annotation.*;

/**
 * Created by Administrator on 2016/11/20.
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NettyMapping {
    public String value() default "";
}
