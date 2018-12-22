package eu.chakhouski.juepak.util;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

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


    private static int checkDeserializeArraySize(int NumElements)
    {
        // Check array size (if negative)
        if (NumElements < 0)
        {
            throw new NegativeArraySizeException(String.format("Array size must be within [%d, %d), given: %d",
                    0, Integer.MAX_VALUE, NumElements));
        }

        return NumElements;
    }

    public static <T> T[] ReadStructArray(ByteBuffer b, Class<T> elementType)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        @SuppressWarnings("unchecked")
        final T[] array = (T[])Array.newInstance(elementType, NumElements);

        try {
            final Constructor<T> defaultConstructor = elementType.getConstructor();

            for (int i = 0; i < NumElements; i++)
            {
                final T item = defaultConstructor.newInstance();
                if (item instanceof UEDeserializable)
                {
                    try {
                        ((UEDeserializable) item).Deserialize(b);
                    }
                    catch (BufferUnderflowException | BufferOverflowException be) {
                        // Print buffer info
                        System.err.println("Buffer exception caused: " + b.toString());

                        // Rethrow exception, it is fatal, unfortunately :C
                        throw be;
                    }
                }
                else
                {
                    throw new IllegalArgumentException(join(lineSeparator(), asList(
                        "Unsupported element class",
                        "   Expected: Subclass of " + UEDeserializable.class,
                        "   Actual: " + elementType
                    )));
                }

                array[i] = item;
            }
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        return array;
    }

    // 1 byte per element
    public static boolean[] ReadBooleanArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final boolean[] array = new boolean[NumElements];

        // Transfer to the array, casting each element to boolean
        for (int i = 0; i < NumElements; array[i++] = b.get() != 0);

        return array;
    }

    public static byte[] ReadByteArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final byte[] array = new byte[NumElements];
        b.get(array);

        return array;
    }

    // 2 bytes per element
    public static char[] ReadCharArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final char[] array = new char[NumElements];
        b.asCharBuffer().get(array);

        return array;
    }

    public static short[] ReadShortArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final short[] array = new short[NumElements];
        b.asShortBuffer().get(array);

        return array;
    }

    // 4 bytes per element
    public static int[] ReadIntArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final int[] array = new int[NumElements];
        b.asIntBuffer().get(array);

        return array;
    }

    public static float[] ReadFloatArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final float[] array = new float[NumElements];
        b.asFloatBuffer().get(array);

        return array;
    }

    // 8 bytes per element
    public static long[] ReadLongArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final long[] array = new long[NumElements];
        b.asLongBuffer().get(array);

        return array;
    }

    public static double[] ReadDoubleArray(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final double[] array = new double[NumElements];
        b.asDoubleBuffer().get(array);

        return array;
    }

    /**
     * Reads a generic array from a buffer.
     * @param b Buffer, from which the data will be read.
     * @param elementType Class of the element of the array.
     * @param <T> Type of the element.
     *
     * @return A newly created array of deserialized items. It is an object because it can be a primitive array.
     */
    public static <T> Object ReadArray(ByteBuffer b, Class<T> elementType)
    {
        b.order(ByteOrder.LITTLE_ENDIAN);

        if (elementType.isPrimitive())
        {
            if (elementType == boolean.class)
                return ReadBooleanArray(b);

            else if (elementType == byte.class)
                return ReadByteArray(b);

            else if (elementType == char.class)
                return ReadCharArray(b);

            else if (elementType == short.class)
                return ReadShortArray(b);

            else if (elementType == int.class)
                return ReadIntArray(b);

            else if (elementType == float.class)
                return ReadFloatArray(b);

            else if (elementType == long.class)
                return ReadLongArray(b);

            else if (elementType == double.class)
                return ReadDoubleArray(b);

            // Unknown primitive type
            throw new IllegalArgumentException("Unknown primitive type: " + elementType);
        }
        else
        {
            return ReadStructArray(b, elementType);
        }
    }
}
