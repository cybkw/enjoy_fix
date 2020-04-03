package com.enjoy.fix.patch.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by mivanzhang on 16/12/9.
 * annotaion used for modify classes or methods,classes and methods will be packed into patch
 * .jar/patch.apk
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Modify {
    String value() default "";
}
