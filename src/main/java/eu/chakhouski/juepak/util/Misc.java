package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FFileIterator;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;
import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.Operator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

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
    public static long MAX_ulong = Long.MAX_VALUE;

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
            return fstructToBoolean(o);
        else
        {
            // No support for complex types
            throw new IllegalArgumentException("Implicit boolean casts are supported only for primitive and FStruct types\n" +
                                               "Given: " + oClass.getName());
        }
    }

    private static boolean fstructToBoolean(Object o)
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
                        // Unreflect operator
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

    public static int rand()
    {
        return (int)(Math.random() * (double)0x7fff);
    }



    // Casts
    public static int toInt(final long longValue)
    {
        if (longValue > (long)Integer.MAX_VALUE || longValue < (long)Integer.MIN_VALUE)
        {
            throw new RuntimeException("Unable to cast " + longValue + " to int, it's out of integer bounds");
        }

        return (int)longValue;
    }

    public static byte toByte(final boolean booleanValue)
    {
        return booleanValue ? (byte)1 : (byte)0;
    }
}
