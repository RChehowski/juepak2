package com.vizor.unreal.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that this element was a C++ operator. {@link Operator#value()} must indicate the purpose of a
 * particular operator (such as '==' or '!=' or '+')
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Operator
{
    String BOOL = "bool";

    String value();
}
