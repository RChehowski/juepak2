package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.Operator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("FieldCanBeLocal")
public class Misc
{
    // Show some extra info if calling operator bool() was unsuccessful
    private static boolean DEBUG_OPERATOR_BOOL = true;

    private static Boolean zeroBoolean = Boolean.FALSE;
    private static Byte zeroByte = (byte) 0;
    private static Short zeroShort = (short) 0;
    private static Character zeroCharacter = (char) 0;

    private static Integer zeroInteger = 0;
    private static Float zeroFloat = (float) 0;
    private static Long zeroLong = (long) 0;
    private static Double zeroDouble = (double) 0;

    private static Map<Class<?>, MethodHandle> operatorBoolCache = new HashMap<>();

    public static Object NULL = null;


    public static boolean BOOL(boolean v)
    {
        return v;
    }

    public static boolean BOOL(byte v)
    {
        return v != (byte)0;
    }

    public static boolean BOOL(short v)
    {
        return v != (short)0;
    }

    public static boolean BOOL(char v)
    {
        return v != (char)0;
    }

    public static boolean BOOL(int v)
    {
        return v != 0;
    }

    public static boolean BOOL(float v)
    {
        return v != .0f;
    }

    public static boolean BOOL(long v)
    {
        return v != 0L;
    }

    public static boolean BOOL(double v)
    {
        return v != .0;
    }

    /**
     * Implicit cast to boolean (C/C++ style)
     *  - Primitive type T is false if and only if it is (T)0.
     *  - Pointer types are false if and only if they are 'null'
     *  - {@link FStruct} is true/false if they have an overloaded {}
     *
     * @param o object to be implicitly casted to boolean.
     * @return Boolean result (true or false)
     */
    public static boolean BOOL(Object o)
    {
        if (o == null)
            return false;

        final Class<?> oClass = o.getClass();

        // Primitive checks
        if (oClass.equals(Boolean.class))
            return !zeroBoolean.equals(o);

        else if (oClass.equals(Byte.class))
            return !zeroByte.equals(o);

        else if (oClass.equals(Short.class))
            return !zeroShort.equals(o);

        else if (oClass.equals(Character.class))
            return !zeroCharacter.equals(o);

        else if (oClass.equals(Integer.class))
            return !zeroInteger.equals(o);

        else if (oClass.equals(Float.class))
            return !zeroFloat.equals(o);

        else if (oClass.equals(Long.class))
            return !zeroLong.equals(o);

        else if (oClass.equals(Double.class))
            return !zeroDouble.equals(o);

        else if (oClass.isAnnotationPresent(FStruct.class))
            return structToBoolean(o);
        else
        {
            // No support for complex types
            throw new IllegalArgumentException("Implicit boolean casts are supported only for primitive and FStruct types\n" +
                                               "Given: " + oClass.getName());
        }
    }

    private static boolean structToBoolean(Object o)
    {
        final Class<?> clazz = o.getClass();

        MethodHandle operatorBool = null;
        if (!operatorBoolCache.containsKey(clazz))
        {
            // It will be added to the map as 'null' (no such operator) even if some exception(s) occurred
            Method operatorBoolMethod = null;

            final Method[] methods = clazz.getMethods();
            for (int i = 0, l = methods.length; (i < l) && (operatorBoolMethod == null); i++)
            {
                Method method = methods[i];
                final Operator operator = method.getAnnotation(Operator.class);

                if ((operator != null) && Operator.BOOL.equals(operator.value()))
                    operatorBoolMethod = method;
            }

            if (operatorBoolMethod != null)
            {
                // Check if the convention is valid
                if (boolean.class.equals(operatorBoolMethod.getReturnType()) ||
                    Boolean.class.equals(operatorBoolMethod.getReturnType()))
                {
                    if (operatorBoolMethod.getParameterCount() == 0)
                    {
                        // Retrieve a method handle of an operator
                        try {
                            operatorBool = MethodHandles.lookup().unreflect(operatorBoolMethod);
                        }
                        catch (IllegalAccessException e) {
                            if (DEBUG_OPERATOR_BOOL)
                            {
                                System.err.println("Unable to unreflect " + clazz.getName() + "." + operatorBoolMethod.getName() +
                                                   "\nReason: " + e.toString());
                            }
                        }
                    }
                    else
                    {
                        if (DEBUG_OPERATOR_BOOL)
                        {
                            System.err.println(clazz.getName() + "." + operatorBoolMethod.getName() +
                                               " must have no params to be used as Operator(\"bool\")");
                        }
                    }
                }
                else
                {
                    if (DEBUG_OPERATOR_BOOL)
                    {
                        System.err.println(clazz.getName() + "." + operatorBoolMethod.getName() +
                                           " must return 'boolean' or 'Boolean' to be used as Operator(\"bool\")");
                    }
                }
            }

            operatorBoolCache.put(clazz, operatorBool);
        }
        else
        {
            operatorBool = operatorBoolCache.get(clazz);
        }


        // assume the object is 'exists' and make it 'true' by default
        boolean operatorBoolInvokeResult = true;

        // Call operator if it was found
        if (operatorBool != null)
        {
            try {
                operatorBoolInvokeResult = (boolean)operatorBool.invoke(o);
            }
            catch (Throwable t) {
                if (DEBUG_OPERATOR_BOOL)
                {
                    System.err.println("Calling '" + clazz.getName() + ".operator bool()' failed\n" +
                                       "Reason: " + t.toString());
                }
            }
        }

        return operatorBoolInvokeResult;
    }

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
        if (!BOOL(expr))
        {
           throw new RuntimeException(String.format(format, (Object[]) args));
        }
    }

    public static void check(Object expr)
    {
        if (!BOOL(expr))
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
