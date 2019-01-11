package eu.chakhouski.juepak.util;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static eu.chakhouski.juepak.util.Misc.BOOL;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;

public class UE4Deserializer
{
    // 1 byte
    public static boolean ReadBoolean(ByteBuffer b)
    {
        return b.order(ByteOrder.LITTLE_ENDIAN).get() != 0;
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


    public static <T> T[] ReadArrayOfStructures(ByteBuffer b, Class<T> elementType)
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
    public static boolean[] ReadArrayOfBooleans(ByteBuffer b)
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

    public static byte[] ReadArrayOfBytes(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final byte[] array = new byte[NumElements];
        b.get(array);

        return array;
    }

    // 2 bytes per element
    public static char[] ReadArrayOfChars(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final char[] array = new char[NumElements];
        b.asCharBuffer().get(array);

        return array;
    }

    public static short[] ReadArrayOfShorts(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final short[] array = new short[NumElements];
        b.asShortBuffer().get(array);

        return array;
    }

    // 4 bytes per element
    public static int[] ReadArrayOfInts(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final int[] array = new int[NumElements];
        b.asIntBuffer().get(array);

        return array;
    }

    public static float[] ReadArrayOfFloats(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final float[] array = new float[NumElements];
        b.asFloatBuffer().get(array);

        return array;
    }

    // 8 bytes per element
    public static long[] ReadArrayOfLongs(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final long[] array = new long[NumElements];
        b.asLongBuffer().get(array);

        return array;
    }

    public static double[] ReadArrayOfDoubles(ByteBuffer b)
    {
        // Read number of elements to deserialize
        final int NumElements = checkDeserializeArraySize(ReadInt(b));

        final double[] array = new double[NumElements];
        b.asDoubleBuffer().get(array);

        return array;
    }

    public static <T> T Read(ByteBuffer b, Class<T> clazz) throws IOException
    {
        if (clazz.isArray())
        {
            @SuppressWarnings("unchecked")
            final T array = (T) ReadArray(b, clazz.getComponentType());

            return array;
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

            // Check raw object
            if (clazz.isInstance(rawReadObject))
            {
                @SuppressWarnings("unchecked")
                final T readObject = (T) rawReadObject;

                return readObject;
            }
            else if (rawReadObject != null)
            {
                throw new IllegalArgumentException("Serialize object mismatch. Expected instance of " + clazz +
                        ", got " + rawReadObject.getClass());
            }
            else
            {
                throw new IllegalArgumentException("Unknown primitive type: " + clazz);
            }
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
                noArgConstructor = clazz.getConstructor();
            }
            catch (NoSuchMethodException e) {
                throw new IOException("Class " + clazz.getName() + " should have a no-argument constructor to be read");
            }

            // Create an instance of class
            final T instance;
            try {
                instance = noArgConstructor.newInstance();
            }
            catch (IllegalAccessException e) {
                throw new IOException("A no-argument constructor of " + clazz.getName() + " must be public (accessible)");
            }
            catch (InstantiationException | InvocationTargetException e) {
                throw new IOException("Unable to instantiate " + clazz.getName() + ": " + e.getMessage());
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
     * @param elementType Class of the element of the array.
     *
     * @return A newly created array of deserialize items. It is an object because it can be a primitive array.
     */
    private static Object ReadArray(ByteBuffer b, Class<?> elementType)
    {
        b.order(ByteOrder.LITTLE_ENDIAN);

        if (elementType.isPrimitive())
        {
            if (elementType == boolean.class)
                return ReadArrayOfBooleans(b);

            else if (elementType == byte.class)
                return ReadArrayOfBytes(b);

            else if (elementType == char.class)
                return ReadArrayOfChars(b);

            else if (elementType == short.class)
                return ReadArrayOfShorts(b);

            else if (elementType == int.class)
                return ReadArrayOfInts(b);

            else if (elementType == float.class)
                return ReadArrayOfFloats(b);

            else if (elementType == long.class)
                return ReadArrayOfLongs(b);

            else if (elementType == double.class)
                return ReadArrayOfDoubles(b);

            // Unknown primitive type
            throw new IllegalArgumentException("Unknown primitive type: " + elementType);
        }
        else
        {
            return ReadArrayOfStructures(b, elementType);
        }
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
