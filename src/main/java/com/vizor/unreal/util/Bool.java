package com.vizor.unreal.util;

import com.vizor.unreal.annotations.FStruct;
import com.vizor.unreal.annotations.Operator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class Bool
{
    private static final Byte zeroByte = (byte) 0;

    private static final Short zeroShort = (short) 0;
    private static final Character zeroCharacter = (char) 0;

    private static final Integer zeroInteger = 0;
    private static final Float zeroFloat = (float) 0;

    private static final Long zeroLong = (long) 0;
    private static final Double zeroDouble = (double) 0;

    // Show some extra info if calling operator bool() was unsuccessful
    private static final boolean DEBUG_OPERATOR_BOOL = true;

    private static final LRU<Class<?>, MethodHandle> operatorBoolCache = new LRU<>(32);

    /**
     * Converts a boolean into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is false, true otherwise.
     */
    public static boolean BOOL(boolean value)
    {
        return value;
    }

    /**
     * Converts a byte into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is 0, true otherwise.
     */
    public static boolean BOOL(byte value)
    {
        return value != (byte)0;
    }

    /**
     * Converts a short into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is 0, true otherwise.
     */
    public static boolean BOOL(short value)
    {
        return value != (short)0;
    }

    /**
     * Converts a char into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is 0, true otherwise.
     */
    public static boolean BOOL(char value)
    {
        return value != (char)0;
    }

    /**
     * Converts a int into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is 0, true otherwise.
     */
    public static boolean BOOL(int value)
    {
        return value != 0;
    }

    /**
     * Converts a float into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is 0, true otherwise.
     */
    public static boolean BOOL(float value)
    {
        return value != .0f;
    }

    /**
     * Converts a long into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is 0, true otherwise.
     */
    public static boolean BOOL(long value)
    {
        return value != 0L;
    }

    /**
     * Converts a double into boolean.
     *
     * @param value A value to be converted to boolean.
     * @return False if the value is 0, true otherwise.
     */
    public static boolean BOOL(double value)
    {
        return value != .0;
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
            return !Boolean.FALSE.equals(o);

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
                                System.err.println("Unable to unreflect " + clazz.getName() + "." +
                                    operatorBoolMethod.getName() + "\nReason: " + e.toString());
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
}
