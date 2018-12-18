package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FFileIterator;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;
import eu.chakhouski.juepak.annotations.Operator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class Misc
{
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static Boolean zeroBoolean = Boolean.FALSE;
    private static Byte zeroByte = (byte) 0;
    private static Short zeroShort = (short) 0;
    private static Character zeroCharacter = (char) 0;

    private static Integer zeroInteger = 0;
    private static Float zeroFloat = (float) 0;
    private static Long zeroLong = (long) 0;
    private static Double zeroDouble = (double) 0;



    public static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

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
    public static boolean $(Object o)
    {
        if (o == null)
            return false;

        final Class<?> oClass = o.getClass();

        // Primitive checks
        if (oClass.equals(Boolean.class))
            return zeroBoolean.equals(o);

        else if (oClass.equals(Byte.class))
            return zeroByte.equals(o);

        else if (oClass.equals(Short.class))
            return zeroShort.equals(o);

        else if (oClass.equals(Character.class))
            return zeroCharacter.equals(o);

        else if (oClass.equals(Integer.class))
            return zeroInteger.equals(o);

        else if (oClass.equals(Float.class))
            return zeroFloat.equals(o);

        else if (oClass.equals(Long.class))
            return zeroLong.equals(o);

        else if (oClass.equals(Double.class))
            return zeroDouble.equals(o);

        // But it may have the 'bool operator()', let's figure it out
        Method operatorBool = null;
        for (Method method : oClass.getMethods())
        {
            final Operator annotation = method.getAnnotation(Operator.class);

            if (Operator.BOOL.equals(annotation.value()))
            {
                operatorBool = method;
            }
        }

        if (operatorBool != null)
        {
            // Check if the convention is valid
            if (!boolean.class.equals(operatorBool.getReturnType()))
            {
                throw new RuntimeException(oClass.getName() + "." + operatorBool.getName() + " must return 'boolean' " +
                        "to be used as Operator(\"bool\")");
            }

            if (operatorBool.getParameterCount() != 0)
            {
                throw new RuntimeException(oClass.getName() + "." + operatorBool.getName() + " must have no params " +
                        "to be used as Operator(\"bool\")");
            }

            // All checks done, call the operator
            try {
                return (Boolean)operatorBool.invoke(o);
            }
            catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        else
        {
            // Otherwise we assume the object is 'exists' and have not overloaded 'bool operator()' and thus return true
            return true;
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
}
