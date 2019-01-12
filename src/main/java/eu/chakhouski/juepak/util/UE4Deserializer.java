package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.annotations.FStruct;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static eu.chakhouski.juepak.util.Misc.BOOL;
import static eu.chakhouski.juepak.util.Misc.toInt;

public class UE4Deserializer
{
    // PRIMITIVE TYPES
    // 1 byte
    public static boolean ReadBoolean(ByteBuffer b)
    {
        return BOOL(b.order(ByteOrder.LITTLE_ENDIAN).get());
    }

    public static byte ReadByte(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).get();
    }

    // 2 byte
    public static char ReadCharacter(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getChar();
    }

    public static short ReadShort(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    // 4 byte
    public static int ReadInt(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public static float ReadFloat(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    // 8 byte
    public static long ReadLong(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    public static double ReadDouble(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).getDouble();
    }

    /**
     * Read a string from byte array in UE4-friendly way.
     *
     * @param b Byte buffer to read from.
     * @return A decoded string.
     */
    private static String ReadString(ByteBuffer b)
    {
        // Ensure order is little endian
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Read string length from the buffer
        int SaveNum = ReadInt(b);

        // NEGATIVE length means the string is coded in UTF_16LE
        // POSITIVE length means the string is coded in
        boolean LoadUCS2Char = SaveNum < 0;
        SaveNum = Math.abs(SaveNum);

        // Retrieve decode charset
        final Charset charset = LoadUCS2Char ? StandardCharsets.UTF_16LE : StandardCharsets.US_ASCII;
        final int BytesPerCharacter = LoadUCS2Char ? 2 : 1;

        // And this is my adaptation, we need to increase a number of bytes 2 times
        final int NumBytes = SaveNum * BytesPerCharacter;

        final byte[] StrBytes = new byte[NumBytes];
        b.get(StrBytes);

        // Compute length, stripping zero characters
        int NumBytesToDecode = NumBytes;

        boolean ZeroBytesAhead = true;
        for (int i = SaveNum - 1; ZeroBytesAhead && (i >= 0); --i)
        {
            for (int j = 0; ZeroBytesAhead && (j < BytesPerCharacter); j++)
                ZeroBytesAhead = StrBytes[(i * BytesPerCharacter) + j] == (byte) 0;

            NumBytesToDecode -= toInt(ZeroBytesAhead) * BytesPerCharacter;
        }

        // Finally, decode the string
        return new String(StrBytes, 0, NumBytesToDecode, charset);
    }

    // ARRAY TYPES
    // 1 byte per element
    private static boolean[] ReadArrayOfBooleans(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final boolean[] array = new boolean[NumElements];

        // Transfer to the array, casting each element to boolean. TODO: Check if sizeof(bool) is always 1 in UE4.
        for (int i = 0; i < NumElements; i++)
        {
            array[i] = BOOL(b.get());
        }

        return array;
    }

    private static byte[] ReadArrayOfBytes(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final byte[] array = new byte[NumElements];
        b.get(array);

        return array;
    }

    // 2 bytes per element
    private static char[] ReadArrayOfChars(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final char[] array = new char[NumElements];
        b.asCharBuffer().get(array);

        return array;
    }

    private static short[] ReadArrayOfShorts(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final short[] array = new short[NumElements];
        b.asShortBuffer().get(array);

        return array;
    }

    // 4 bytes per element
    private static int[] ReadArrayOfInts(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final int[] array = new int[NumElements];
        b.asIntBuffer().get(array);

        return array;
    }

    private static float[] ReadArrayOfFloats(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final float[] array = new float[NumElements];
        b.asFloatBuffer().get(array);

        return array;
    }

    // 8 bytes per element
    private static long[] ReadArrayOfLongs(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final long[] array = new long[NumElements];
        b.asLongBuffer().get(array);

        return array;
    }

    private static double[] ReadArrayOfDoubles(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final double[] array = new double[NumElements];
        b.asDoubleBuffer().get(array);

        return array;
    }

    private static <T> T ReadArrayOfStructures(ByteBuffer b, Class<T> arrayType)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        if (!arrayType.isArray())
            throw new IllegalArgumentException("Only valid for arrays");

        final Class<?> componentType = arrayType.getComponentType();

        @SuppressWarnings("unchecked")
        final T array = (T)Array.newInstance(componentType, NumElements);

        // Put into array
        for (int i = 0; i < NumElements; i++)
            Array.set(array, i, Read(b, componentType));

        return array;
    }

    /**
     * A generic read method.
     *
     * @param b Buffer to read from.
     * @param clazz A class to read.
     * @return Class instance.
     */
    public static <T> T Read(ByteBuffer b, Class<T> clazz)
    {
        if (clazz.isArray())
        {
            return ReadArray(b, clazz);
        }
        else if (clazz.isPrimitive())
        {
            final Object rawReadObject;

            if (clazz == boolean.class)
                rawReadObject = ReadBoolean(b);

            else if (clazz == byte.class)
                rawReadObject = ReadByte(b);

            else if (clazz == char.class)
                rawReadObject = ReadCharacter(b);

            else if (clazz == short.class)
                rawReadObject = ReadShort(b);

            else if (clazz == int.class)
                rawReadObject = ReadInt(b);

            else if (clazz == float.class)
                rawReadObject = ReadFloat(b);

            else if (clazz == long.class)
                rawReadObject = ReadLong(b);

            else if (clazz == double.class)
                rawReadObject = ReadDouble(b);

            else
                rawReadObject = null;

            @SuppressWarnings("unchecked")
            final T readObject = (T) Objects.requireNonNull(rawReadObject, "Raw object mustn't be null");

            return readObject;
        }
        else if (String.class.isAssignableFrom(clazz))
        {
            // Class is actually a string class and T is String, but compiler can not yield
            @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked"})
            final T stringResult = (T) ReadString(b);

            return stringResult;
        }
        else if (UEDeserializable.class.isAssignableFrom(clazz))
        {
            // Retrieve a no-argument constructor
            final Constructor<T> noArgConstructor;
            try {
                noArgConstructor = clazz.getDeclaredConstructor();
            }
            catch (NoSuchMethodException e) {
                throw new RuntimeException(clazz.getName() + " should have a no-argument constructor to be read", e);
            }

            // Create an instance of class
            final T instance;
            try {
                instance = noArgConstructor.newInstance();
            }
            catch (IllegalAccessException e) {
                throw new RuntimeException("A no-argument constructor of " + clazz.getName() + " must be public (accessible)", e);
            }
            catch (InstantiationException | InvocationTargetException e) {
                throw new RuntimeException("Unable to instantiate " + clazz.getName(), e);
            }

            // Deserialize an instance
            ((UEDeserializable)instance).Deserialize(b);

            // Return an instance
            return instance;
        }
        else
        {
            throw new IllegalArgumentException("Unable to deserialize " + clazz + ", don't know how to read it");
        }
    }

    /**
     * Reads a generic array from a buffer.
     * NOTE: To deserialize an array of structures you should better use a generic {@link #ReadArrayOfStructures(ByteBuffer, Class)}
     *       Because it provides a generic structure type.
     *
     * @param b Buffer, from which the data will be read.
     * @param arrayType Class of the element of the array.
     *
     * @return A newly created array of deserialize items. It is an object because it can be a primitive array.
     */
    private static <T> T ReadArray(ByteBuffer b, Class<T> arrayType)
    {
        b.order(ByteOrder.LITTLE_ENDIAN);

        if (!arrayType.isArray())
            throw new IllegalArgumentException(arrayType.getName() + " is not an array type");

        final Class<?> arrayComponentType = arrayType.getComponentType();

        if (arrayComponentType.isPrimitive())
        {
            if (arrayComponentType == boolean.class)
                return (T)ReadArrayOfBooleans(b);

            else if (arrayComponentType == byte.class)
                return (T)ReadArrayOfBytes(b);

            else if (arrayComponentType == char.class)
                return (T)ReadArrayOfChars(b);

            else if (arrayComponentType == short.class)
                return (T)ReadArrayOfShorts(b);

            else if (arrayComponentType == int.class)
                return (T)ReadArrayOfInts(b);

            else if (arrayComponentType == float.class)
                return (T)ReadArrayOfFloats(b);

            else if (arrayComponentType == long.class)
                return (T)ReadArrayOfLongs(b);

            else if (arrayComponentType == double.class)
                return (T)ReadArrayOfDoubles(b);

            // Unknown primitive type
            throw new IllegalArgumentException("Unknown primitive type: " + arrayType);
        }
        else if (arrayComponentType.isAnnotationPresent(FStruct.class))
        {
            return ReadArrayOfStructures(b, arrayType);
        }
        else
        {
            throw new IllegalArgumentException("Unable to serialize " + arrayType);
        }
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
}
