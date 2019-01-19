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
        // 1 byte
        sizeCache.put(boolean.class, Byte.BYTES);
        sizeCache.put(byte.class, Byte.BYTES);

        // 2 bytes
        sizeCache.put(short.class, Short.BYTES);
        sizeCache.put(char.class, Character.BYTES);

        // 4 bytes
        sizeCache.put(int.class, Integer.BYTES);
        sizeCache.put(float.class, Float.BYTES);

        // 8 bytes
        sizeCache.put(long.class, Long.BYTES);
        sizeCache.put(double.class, Double.BYTES);

        // 0 bytes
        sizeCache.put(void.class, 0);
    }


    @SuppressWarnings("unused")
    public static int sizeof(boolean $) { return Byte.BYTES; }

    @SuppressWarnings("unused")
    public static int sizeof(byte $)    { return Byte.BYTES; }

    @SuppressWarnings("unused")
    public static int sizeof(short $)   { return Short.BYTES; }

    @SuppressWarnings("unused")
    public static int sizeof(char $)    { return Character.BYTES; }

    @SuppressWarnings("unused")
    public static int sizeof(int $)     { return Integer.BYTES; }

    @SuppressWarnings("unused")
    public static int sizeof(long $)    { return Long.BYTES; }

    @SuppressWarnings("unused")
    public static int sizeof(float $)   { return Float.BYTES; }

    @SuppressWarnings("unused")
    public static int sizeof(double $)  { return Double.BYTES; }


    public static int sizeof(boolean[] a) { return a.length * Byte.BYTES; }
    public static int sizeof(byte[] a)    { return a.length * Byte.BYTES; }

    public static int sizeof(short[] a)   { return a.length * Short.BYTES; }
    public static int sizeof(char[] a)    { return a.length * Character.BYTES; }

    public static int sizeof(int[] a)     { return a.length * Integer.BYTES; }
    public static int sizeof(long[] a)    { return a.length * Long.BYTES; }

    public static int sizeof(float[] a)   { return a.length * Float.BYTES; }
    public static int sizeof(double[] a)  { return a.length * Double.BYTES; }


    public static int sizeof(Object object)
    {
        final Class<?> clazz;

        if (object instanceof Class<?>)
            clazz = (Class<?>)object;

        else if (object != null)
            clazz = object.getClass();

        else
            clazz = void.class;

        return getStructSize(clazz);
    }

    private static int getStructSize(Class<?> clazz)
    {
        final Integer cachedSize = sizeCache.get(clazz);
        if (cachedSize != null)
        {
            return cachedSize;
        }
        else if (clazz.isAnnotationPresent(FStruct.class))
        {
            // Mustn't have any superclasses
            if (Object.class == clazz.getSuperclass())
            {
                int size = 0;
                for (final Field field : clazz.getDeclaredFields())
                {
                    if (!Modifier.isStatic(field.getModifiers()))
                    {
                        final StaticSize staticSize = field.getAnnotation(StaticSize.class);

                        final int elementSize = getStructSize(field.getType());
                        final int dimension = staticSize != null ? staticSize.value() : 1;

                        size += elementSize * dimension;
                    }
                }
                return size;
            }
            else
            {
                throw new RuntimeException("FStruct must only extend Object");
            }

        }
        else
        {
            throw new RuntimeException("Size of " + clazz + " cannot be determined");
        }
    }
}
