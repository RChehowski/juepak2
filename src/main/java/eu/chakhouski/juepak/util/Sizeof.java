package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.StaticSize;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class Sizeof
{
    private static final Map<Class<?>, Integer> sizeCache = new HashMap<>();

    static {
        sizeCache.put(boolean.class, Byte.BYTES);
        sizeCache.put(byte.class, Byte.BYTES);

        sizeCache.put(short.class, Short.BYTES);
        sizeCache.put(char.class, Character.BYTES);

        sizeCache.put(int.class, Integer.BYTES);
        sizeCache.put(float.class, Float.BYTES);

        sizeCache.put(long.class, Long.BYTES);
        sizeCache.put(double.class, Double.BYTES);
    }



    public static int sizeof(boolean ignore) { return Byte.BYTES; }
    public static int sizeof(byte ignore) { return Byte.BYTES; }

    public static int sizeof(short ignore) { return Short.BYTES; }
    public static int sizeof(char ignore) { return Character.BYTES; }

    public static int sizeof(int ignore) { return Integer.BYTES; }
    public static int sizeof(long ignore) { return Long.BYTES; }

    public static int sizeof(float ignore) { return Float.BYTES; }
    public static int sizeof(double ignore) { return Double.BYTES; }



    public static int sizeof(boolean[] array) { return Byte.BYTES * array.length; }
    public static int sizeof(byte[] array) { return Byte.BYTES * array.length; }

    public static int sizeof(short[] array) { return Short.BYTES * array.length; }
    public static int sizeof(char[] array) { return Character.BYTES * array.length; }

    public static int sizeof(int[] array) { return Integer.BYTES * array.length; }
    public static int sizeof(long[] array) { return Long.BYTES * array.length; }

    public static int sizeof(float[] array) { return Float.BYTES * array.length; }
    public static int sizeof(double[] array) { return Double.BYTES * array.length; }

    public static int sizeof(Class<?> clazz)
    {
        return sizeCache.computeIfAbsent(clazz, Sizeof::getStructSize);
    }

    public static int sizeof(Object object)
    {
        return sizeof(object.getClass());
    }

    private static int getStructSize(Class<?> clazz)
    {
        if (sizeCache.containsKey(clazz))
        {
            return sizeCache.get(clazz);
        }
        else if (clazz.isAnnotationPresent(FStruct.class))
        {
            if (!Object.class.equals(clazz.getSuperclass()))
            {
                throw new RuntimeException("FStruct must only extend Object");
            }

            // Interfaces such as UESerializable and UEDeserializable are allowed

            int calculatedSize = 0;
            final Field[] fields = clazz.getDeclaredFields();

            for (int i = 0, length = fields.length; i < length; i++)
            {
                final Field f = fields[i];

                if (!Modifier.isStatic(f.getModifiers()))
                {
                    if (f.isAnnotationPresent(StaticSize.class))
                    {
                        calculatedSize += f.getAnnotation(StaticSize.class).value();
                    }
                    else
                    {
                        calculatedSize += getStructSize(f.getType());
                    }
                }
            }

            return calculatedSize;
        }
        else
        {
            throw new RuntimeException(clazz.getName() + " is neither primitive nor FStruct, and thus it's size " +
                    "cannot be determined");
        }
    }
}
