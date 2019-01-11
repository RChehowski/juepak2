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

                        final int dimension;
                        if (staticSize != null)
                            dimension = staticSize.value();
                        else
                            dimension = 1;

                        final int elementSize = getStructSize(field.getType());
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
