package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FFileIterator;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;

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

        return false;
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
