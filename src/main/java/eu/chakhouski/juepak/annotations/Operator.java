package eu.chakhouski.juepak.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that this element was a C++ operator. {@link Operator#value()} must indicate the purpose of a
 * particular operator (such as '==' or '!=' or '+')
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Operator
{
    String value();
}
