package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FFileIterator;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;
import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.Operator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

@SuppressWarnings("FieldCanBeLocal")
public class Misc
{
    private static Boolean zeroBoolean = Boolean.FALSE;
    private static Byte zeroByte = (byte) 0;
    private static Short zeroShort = (short) 0;
    private static Character zeroCharacter = (char) 0;

    private static Integer zeroInteger = 0;
    private static Float zeroFloat = (float) 0;
    private static Long zeroLong = (long) 0;
    private static Double zeroDouble = (double) 0;

    private static Map<Class<?>, MethodHandle> operatorBoolCache = new HashMap<>();

    public static Map<String, FPakEntry> GetSortedEntries(FPakFile PakFile, Comparator<FPakEntry> Comparator)
    {
        final TreeMap<FPakEntry, String> Map = new TreeMap<>(Comparator);

        // Sort map
        for (FFileIterator Iterator = PakFile.iterator(); Iterator.hasNext(); )
            Map.put(Iterator.next(), Iterator.Filename());

        final Map<String, FPakEntry> ResultMap = new LinkedHashMap<>();
        Map.forEach((k, v) -> ResultMap.put(v, k));

        return ResultMap;
    }

    /**
     * Implicit cast to boolean (C++ style)
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
        {
            MethodHandle operatorBool = null;
            if (!operatorBoolCache.containsKey(oClass))
            {
                final Method[] declaredMethods = oClass.getDeclaredMethods();
                Method operatorBoolMethod = null;

                for (int i = 0, l = declaredMethods.length; (i < l) && (operatorBoolMethod == null); i++)
                {
                    Method declaredMethod = declaredMethods[i];
                    final Operator operator = declaredMethod.getAnnotation(Operator.class);

                    if ((operator != null) && Operator.BOOL.equals(operator.value()))
                        operatorBoolMethod = declaredMethod;
                }

                if (operatorBoolMethod != null)
                {
                    final boolean methodWasAccessible = operatorBoolMethod.isAccessible();
                    if (methodWasAccessible)
                        operatorBoolMethod.setAccessible(true);


                    // Check if the convention is valid
                    if (!boolean.class.equals(operatorBoolMethod.getReturnType()) &&
                        !Boolean.class.equals(operatorBoolMethod.getReturnType()))
                    {
                        throw new RuntimeException(oClass.getName() + "." + operatorBoolMethod.getName() +
                                " must return 'boolean' or 'Boolean' to be used as Operator(\"bool\")");
                    }

                    if (operatorBoolMethod.getParameterCount() != 0)
                    {
                        throw new RuntimeException(oClass.getName() + "." + operatorBoolMethod.getName() +
                                " must have no params to be used as Operator(\"bool\")");
                    }

                    // Call the operator
                    try {
                        operatorBool = MethodHandles.lookup().unreflect(operatorBoolMethod);
                    }
                    catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }

                    if (!methodWasAccessible)
                        operatorBoolMethod.setAccessible(false);
                }

                operatorBoolCache.put(oClass, operatorBool);
            }
            else
            {
                operatorBool = operatorBoolCache.get(oClass);
            }


            if (operatorBool != null)
            {
                try {
                    return (boolean)operatorBool.invoke(o);
                }
                catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            }
            else
            {
                // Otherwise we assume the object is 'exists' and have not overloaded 'bool operator()' and thus return true
                return true;
            }
        }
        else
        {
            // No support for complex types yet
            throw new RuntimeException("Implicit boolean casts are supported only for primitive and FStruct types");
        }
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
        throw new RuntimeException(String.format(format, (Object[])args));
    }
}
