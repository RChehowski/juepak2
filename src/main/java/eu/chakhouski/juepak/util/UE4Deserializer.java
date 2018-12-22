package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.annotations.FStruct;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static eu.chakhouski.juepak.util.Sizeof.sizeof;
import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;

public class UE4Deserializer
{
    public static String ReadString(ByteBuffer b)
    {
        // Ensure order is little endian
        b.order(ByteOrder.LITTLE_ENDIAN);

        int SaveNum = ReadInt(b);

        boolean LoadUCS2Char = SaveNum < 0;
        if (LoadUCS2Char)
        {
            SaveNum = -SaveNum;
            SaveNum *= 2;
        }

        final byte[] strBytes = new byte[SaveNum];
        b.get(strBytes);

        // Create a string excluding null characters
        final String Result;
        if (LoadUCS2Char)
        {
            Result = new String(strBytes, 0, SaveNum, StandardCharsets.UTF_16LE);
        }
        else
        {
            Result = new String(strBytes, 0, SaveNum, StandardCharsets.US_ASCII);
        }

        return Result.replaceAll("\0", "");
    }

    public static int ReadInt(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getInt();
    }


    public static long ReadLong(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getLong();
    }


    public static <T> T[] ReadStructArray(ByteBuffer b, Class<T> elementType)
    {
        // Read number of elements to deserialize
        final int NumElements = ReadInt(b);

        @SuppressWarnings("unchecked")
        final T[] array = (T[])Array.newInstance(elementType, NumElements);

        try
        {
            final Constructor<T> defaultConstructor = elementType.getConstructor();

            for (int i = 0; i < NumElements; i++)
            {
                final T item = defaultConstructor.newInstance();
                if (item instanceof UEDeserializable)
                {
                    ((UEDeserializable) item).Deserialize(b);
                }
                else
                {
                    throw new IllegalArgumentException("Can not deserialize " + elementType +
                            " not an instance of " + UEDeserializable.class);
                }

                array[i] = item;
            }
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException(e);
        }

        return array;
    }

    public static <T> Object ReadArray(ByteBuffer b, Class<T> elementType)
    {
        b.order(ByteOrder.LITTLE_ENDIAN);

        if (!elementType.isAnnotationPresent(FStruct.class))
        {
            throw new IllegalArgumentException(join(lineSeparator(), asList(
                "Unsupported element class",
                "   Expected: Subclass of " + FStruct.class,
                "   Actual: " + elementType
            )));
        }

        if (elementType.isPrimitive())
        {
            // Read number of elements to deserialize
            final int NumElements = ReadInt(b);

            if (elementType == boolean.class)
            {
                final boolean[] array = new boolean[NumElements];

                // Transfer to the array, casting each element to boolean
                for (int i = 0; i < NumElements; array[i++] = b.get() != 0);

                return array;

            }
            else if (elementType == byte.class)
            {
                final byte[] array = new byte[NumElements];
                b.get(array);

                return array;
            }
            else if (elementType == char.class)
            {
                final char[] array = new char[NumElements];
                b.asCharBuffer().get(array);

                return array;
            }
            else if (elementType == short.class)
            {
                final short[] array = new short[NumElements];
                b.asShortBuffer().get(array);

                return array;
            }
            else if (elementType == int.class)
            {
                final int[] array = new int[NumElements];
                b.asIntBuffer().get(array);

                return array;
            }
            else if (elementType == float.class)
            {
                final float[] array = new float[NumElements];
                b.asFloatBuffer().get(array);

                return array;
            }
            else if (elementType == long.class)
            {
                final long[] array = new long[NumElements];
                b.asLongBuffer().get(array);

                return array;
            }
            else if (elementType == double.class)
            {
                final double[] array = new double[NumElements];
                b.asDoubleBuffer().get(array);

                return array;
            }
            else
            {
                throw new IllegalArgumentException("Unknown primitive type: " + elementType);
            }
        }
        else
        {
            return ReadStructArray(b, elementType);
        }
    }
}
