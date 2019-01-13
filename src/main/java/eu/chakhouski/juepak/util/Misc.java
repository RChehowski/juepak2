package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.annotations.Operator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("FieldCanBeLocal")
public class Misc
{
    public static Object NULL = null;


    /**
     * Just a wrapper for java string making for us possible to keep the UE4 convention.
     *
     * @param s String to be converted to the text.
     * @return Actually, the same string.
     */
    public static String TEXT(String s)
    {
        return s;
    }

    public static char TEXT(char c)
    {
        return c;
    }

    public static char TCHAR(char c)
    {
        return c;
    }

    public static char TCHAR(int i)
    {
        return (char)i;
    }

    public static void checkf(Object expr, String format, Object... args)
    {
        if (!Bool.BOOL(expr))
        {
           throw new RuntimeException(String.format(format, (Object[]) args));
        }
    }

    public static void check(Object expr)
    {
        if (!Bool.BOOL(expr))
        {
            throw new RuntimeException();
        }
    }

    /**
     * Generates a random value between 0 and {@link Short#MAX_VALUE}
     * This is a reference implementation of {@see http://www.cplusplus.com/reference/cstdlib/rand/}.
     *
     * @return A random value.
     */
    public static int rand()
    {
        return (int)(Math.random() * (double)0x7fff);
    }


    /**
     * Converts a long value to int value, raising an exception if a long value was out of bounds.
     * Java does not allow an implicit conversion between long and int, explicit conversion simply looses precision.
     *
     * @param value Long value to be converted to int.
     * @return Integer value.
     */
    public static int toInt(final long value)
    {
        if (value > (long)Integer.MAX_VALUE || value < (long)Integer.MIN_VALUE)
        {
            throw new IllegalArgumentException("Unable to cast " + value + " to int, it's out of integer bounds");
        }

        return (int)value;
    }

    public static int toInt(final boolean value)
    {
        return value ? 1 : 0;
    }

    /**
     * Converts a boolean value into byte value.
     *
     * @param booleanValue A boolean value to be converted into byte.
     * @return Byte value.
     */
    public static byte toByte(final boolean booleanValue)
    {
        return booleanValue ? (byte)1 : (byte)0;
    }


}
