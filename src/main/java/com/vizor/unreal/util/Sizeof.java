package com.vizor.unreal.util;

import com.vizor.unreal.annotations.FStruct;
import com.vizor.unreal.annotations.StaticSize;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings({"unused", "SameReturnValue"})
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


    // Size of primitives
    public static int sizeof(boolean $) { return Byte.BYTES; }
    public static int sizeof(byte $)    { return Byte.BYTES; }
    public static int sizeof(short $)   { return Short.BYTES; }
    public static int sizeof(char $)    { return Character.BYTES; }
    public static int sizeof(int $)     { return Integer.BYTES; }
    public static int sizeof(long $)    { return Long.BYTES; }
    public static int sizeof(float $)   { return Float.BYTES; }
    public static int sizeof(double $)  { return Double.BYTES; }

    // Size of arrays of primitives
    public static int sizeof(boolean[] $) { return $.length * Byte.BYTES; }
    public static int sizeof(byte[] $)    { return $.length * Byte.BYTES; }
    public static int sizeof(short[] $)   { return $.length * Short.BYTES; }
    public static int sizeof(char[] $)    { return $.length * Character.BYTES; }
    public static int sizeof(int[] $)     { return $.length * Integer.BYTES; }
    public static int sizeof(long[] $)    { return $.length * Long.BYTES; }
    public static int sizeof(float[] $)   { return $.length * Float.BYTES; }
    public static int sizeof(double[] $)  { return $.length * Double.BYTES; }

    /**
     * Retrieves a size of an object.
     *
     * @param o An object (or class of the object), whose size has to be calculated
     * @return A
     */
    public static int sizeof(final Object o)
    {
        if (o != null)
        {
            return getStructSize(classOf(o));
        }

        return -1;
    }

    private static Class<?> classOf(final Object o)
    {
        Objects.requireNonNull(o, "Can not retrieve class of a null object");

        if (o instanceof Class<?>)
        {
            return (Class<?>) o;
        }

        return o.getClass();
    }

    private static int getStructSize(final Class<?> clazz)
    {
        final Integer cachedSize = sizeCache.get(clazz);
        if (cachedSize != null)
        {
            // Already cached (works for primitives as well)
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
                    // Exclude static
                    if (!Modifier.isStatic(field.getModifiers()))
                    {
                        final StaticSize staticSize = field.getAnnotation(StaticSize.class);

                        final int elementSize = getStructSize(field.getType());
                        final int dimension = staticSize != null ? staticSize.value() : 1;

                        size += elementSize * dimension;
                    }
                }

                sizeCache.put(clazz, size);
                return size;
            }
            else
            {
                throw new RuntimeException("\"" + classToString(clazz) + "\" must only extend java.lang.Object");
            }

        }
        else
        {
            throw new RuntimeException("\"" + classToString(clazz) + "\" must only extend java.lang.Object");
        }
    }

    private static String classToString(final Class<?> clazz)
    {
        final StringBuilder sb = new StringBuilder();

        final int modifiers = clazz.getModifiers();
        final Class<?> superclass = clazz.getSuperclass();
        final Class<?>[] interfaces = clazz.getInterfaces();
        final Annotation[] annotations = clazz.getAnnotations();

        // Write annotations
        for (final Annotation a : annotations)
            sb.append('@').append(getPrettyClassName(a.annotationType())).append(' ');

        // Write modifier
        if (Modifier.isPublic(modifiers))
            sb.append("public").append(' ');
        if (Modifier.isProtected(modifiers))
            sb.append("protected").append(' ');
        if (Modifier.isPrivate(modifiers))
            sb.append("private").append(' ');
        if (Modifier.isFinal(modifiers))
            sb.append("final").append(' ');
        if (Modifier.isAbstract(modifiers))
            sb.append("abstract").append(' ');
        if (Modifier.isStrict(modifiers))
            sb.append("strictfp").append(' ');

        // Write class header
        sb.append("class").append(' ').append(getPrettyClassName(clazz));

        // Write superclass
        if (superclass != null)
            sb.append(' ').append("extends").append(' ').append(getPrettyClassName(superclass));

        // Write interfaces
        if (interfaces.length > 0)
        {
            sb.append(' ').append("implements");

            for (Class<?> anInterface : interfaces)
                sb.append(' ').append(getPrettyClassName(anInterface));
        }

        return sb.toString();
    }

    private static String getPrettyClassName(final Class<?> clazz)
    {
        if (clazz != null)
        {
            if (clazz.isAnonymousClass())
                return clazz.getName();

            return clazz.getSimpleName();
        }

        return "<null class>";
    }
}
