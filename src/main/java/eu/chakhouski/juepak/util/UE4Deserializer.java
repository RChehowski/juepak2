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

import static eu.chakhouski.juepak.util.Bool.BOOL;

/**
 * Used to de-serialize data from Unreal Engine FArchives.
 */
@SuppressWarnings("WeakerAccess")
public class UE4Deserializer
{
    /**
     * LRU cache for some hot constructors
     */
    private static final LRU<Class<?>, Constructor<?>> cachedNoArgConstructors = new LRU<>(32);

    // PRIMITIVE TYPES
    // 1 byte

    /**
     * Reads a single 1-byte boolean from the buffer.
     * @param b
     * @return
     */
    public static boolean ReadBoolean(ByteBuffer b)
    {
        setLittleEndian(b);
        return BOOL(b.get());
    }

    public static byte ReadByte(ByteBuffer b)
    {
        setLittleEndian(b);
        return b.get();
    }

    // 2 byte
    public static char ReadCharacter(ByteBuffer b)
    {
        setLittleEndian(b);
        return b.getChar();
    }

    public static short ReadShort(ByteBuffer b)
    {
        setLittleEndian(b);
        return b.getShort();
    }

    // 4 byte
    public static int ReadInt(ByteBuffer b)
    {
        setLittleEndian(b);
        return b.getInt();
    }

    public static float ReadFloat(ByteBuffer b)
    {
        setLittleEndian(b);
        return b.getFloat();
    }

    // 8 byte
    public static long ReadLong(ByteBuffer b)
    {
        setLittleEndian(b);
        return b.getLong();
    }

    public static double ReadDouble(ByteBuffer b)
    {
        setLittleEndian(b);
        return b.getDouble();
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

            if (ZeroBytesAhead)
                NumBytesToDecode -= BytesPerCharacter;
        }

        // Finally, decode the string
        return new String(StrBytes, 0, NumBytesToDecode, charset);
    }

    @SuppressWarnings("unchecked")
    private static <T> T ReadPrimitiveWrapper(ByteBuffer b, Class<T> clazz)
    {
        Objects.requireNonNull(clazz, "Class should not be null");

        if (clazz == Boolean.class)
            return (T)Boolean.valueOf(ReadBoolean(b));

        else if (clazz == Byte.class)
            return (T)Byte.valueOf(ReadByte(b));

        else if (clazz == Character.class)
            return (T)Character.valueOf(ReadCharacter(b));

        else if (clazz == Short.class)
            return (T)Short.valueOf(ReadShort(b));

        else if (clazz == Integer.class)
            return (T)Integer.valueOf(ReadInt(b));

        else if (clazz == Float.class)
            return (T)Float.valueOf(ReadFloat(b));

        else if (clazz == Long.class)
            return (T)Long.valueOf(ReadLong(b));

        else if (clazz == Double.class)
            return (T)Double.valueOf(ReadDouble(b));

        throw new IllegalArgumentException("Invalid or unknown wrapper type: " + clazz.getName());
    }

    @SuppressWarnings("unchecked")
    private static <T> T ReadBoxedPrimitive(ByteBuffer b, Class<T> clazz)
    {
        Objects.requireNonNull(clazz, "Class should not be null");

        if (clazz == boolean.class)
            return (T)Boolean.valueOf(ReadBoolean(b));

        else if (clazz == byte.class)
            return (T)Byte.valueOf(ReadByte(b));

        else if (clazz == char.class)
            return (T)Character.valueOf(ReadCharacter(b));

        else if (clazz == short.class)
            return (T)Short.valueOf(ReadShort(b));

        else if (clazz == int.class)
            return (T)Integer.valueOf(ReadInt(b));

        else if (clazz == float.class)
            return (T)Float.valueOf(ReadFloat(b));

        else if (clazz == long.class)
            return (T)Long.valueOf(ReadLong(b));

        else if (clazz == double.class)
            return (T)Double.valueOf(ReadDouble(b));


        throw new IllegalArgumentException("Invalid or unknown primitive type: " + clazz.getName());
    }

    private static <T> T ReadObject(ByteBuffer b, Class<T> clazz)
    {
        // Retrieve a no-argument constructor
        Constructor<?> noArgConstructor = cachedNoArgConstructors.get(clazz);
        if (noArgConstructor == null)
        {
            try {
                noArgConstructor = clazz.getDeclaredConstructor();
            }
            catch (NoSuchMethodException e) {
                throw new RuntimeException(clazz.getName() + " should have a no-argument constructor to be read", e);
            }

            cachedNoArgConstructors.put(clazz, noArgConstructor);
        }

        // Create an instance of class
        try {
            @SuppressWarnings("unchecked")
            final T instance = (T) noArgConstructor.newInstance();

            // Deserialize an instance
            ((UEDeserializable) instance).Deserialize(b);

            // Return an instance
            return instance;
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException("A no-argument constructor of " + clazz.getName() + " must be public (accessible)", e);
        }
        catch (InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("Unable to instantiate " + clazz.getName(), e);
        }
    }

    // ARRAY TYPES
    // 1 byte per element
    private static boolean[] ReadArrayOfBooleans(ByteBuffer b, int numElements)
    {
        final boolean[] array = new boolean[numElements];

        // Transfer to the array, casting each element to boolean.
        for (int i = 0; i < numElements; i++)
            array[i] = BOOL(b.get());

        return array;
    }

    private static byte[] ReadArrayOfBytes(ByteBuffer b, int numElements)
    {
        final byte[] array = new byte[numElements];
        b.get(array);

        return array;
    }

    // 2 bytes per element
    private static char[] ReadArrayOfChars(ByteBuffer b, int numElements)
    {
        final char[] array = new char[numElements];
        b.asCharBuffer().get(array);

        return array;
    }

    private static short[] ReadArrayOfShorts(ByteBuffer b, int numElements)
    {
        final short[] array = new short[numElements];
        b.asShortBuffer().get(array);

        return array;
    }

    // 4 bytes per element
    private static int[] ReadArrayOfInts(ByteBuffer b, int numElements)
    {
        final int[] array = new int[numElements];
        b.asIntBuffer().get(array);

        return array;
    }

    private static float[] ReadArrayOfFloats(ByteBuffer b, int numElements)
    {
        final float[] array = new float[numElements];
        b.asFloatBuffer().get(array);

        return array;
    }

    // 8 bytes per element
    private static long[] ReadArrayOfLongs(ByteBuffer b, int numElements)
    {
        final long[] array = new long[numElements];
        b.asLongBuffer().get(array);

        return array;
    }

    private static double[] ReadArrayOfDoubles(ByteBuffer b, int numElements)
    {
        setLittleEndian(b);

        final double[] array = new double[numElements];
        b.asDoubleBuffer().get(array);

        return array;
    }

    private static <T> T ReadArrayOfPrimitives(ByteBuffer b, Class<T> arrayType, int numElements)
    {
        final Class<?> arrayComponentType = arrayType.getComponentType();

        if (arrayComponentType == boolean.class)
            return (T)ReadArrayOfBooleans(b, numElements);

        else if (arrayComponentType == byte.class)
            return (T)ReadArrayOfBytes(b, numElements);

        else if (arrayComponentType == char.class)
            return (T)ReadArrayOfChars(b, numElements);

        else if (arrayComponentType == short.class)
            return (T)ReadArrayOfShorts(b, numElements);

        else if (arrayComponentType == int.class)
            return (T)ReadArrayOfInts(b, numElements);

        else if (arrayComponentType == float.class)
            return (T)ReadArrayOfFloats(b, numElements);

        else if (arrayComponentType == long.class)
            return (T)ReadArrayOfLongs(b, numElements);

        else if (arrayComponentType == double.class)
            return (T)ReadArrayOfDoubles(b, numElements);

        // Unknown primitive type
        throw new IllegalArgumentException("Unknown primitive type: " + arrayType);
    }

    private static <T> T ReadArrayOfObjects(ByteBuffer b, Class<T> arrayType, int numElements)
    {
        if (!arrayType.isArray())
            throw new IllegalArgumentException("Only valid for arrays");

        final Class<?> componentType = arrayType.getComponentType();

        @SuppressWarnings("unchecked")
        final T array = (T)Array.newInstance(componentType, numElements);

        // Put into array
        for (int i = 0; i < numElements; i++)
        {
            Array.set(array, i, Read(b, componentType));
        }

        return array;
    }

    /**
     * A generic read method, you should call it to deserialize anything not primitive.
     *
     * @param b Buffer to read from.
     * @param clazz A class to read.
     * @return Class instance.
     */
    public static <T> T Read(ByteBuffer b, Class<T> clazz)
    {
        setLittleEndian(b);

        if (clazz.isArray())
        {
            return ReadArray(b, clazz);
        }
        else if (clazz.isPrimitive())
        {
            return ReadBoxedPrimitive(b, clazz);
        }
        else if (String.class == clazz)
        {
            // Class is actually a string class and T is String, but compiler can not yield
            @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked"})
            final T stringResult = (T) ReadString(b);

            return stringResult;
        }
        else if (UEDeserializable.class.isAssignableFrom(clazz))
        {
            return ReadObject(b, clazz);
        }
        else if (clazz == Boolean.class || clazz == Character.class || Number.class.isAssignableFrom(clazz))
        {
            return ReadPrimitiveWrapper(b, clazz);
        }

        throw new IllegalArgumentException("Unable to deserialize " + clazz + ", don't know how to read it");
    }

    /**
     * Reads a generic array from a buffer.
     * NOTE: To deserialize an array of structures you should better use a generic {@link #ReadArrayOfObjects(ByteBuffer, Class, int)}
     *       Because it provides a generic structure type.
     *
     * @param b Buffer, from which the data will be read.
     * @param arrayType Class of the element of the array.
     *
     * @return A newly created array of deserialize items. It is an object because it can be a primitive array.
     */
    private static <T> T ReadArray(ByteBuffer b, Class<T> arrayType)
    {
        final Class<?> arrayComponentType = arrayType.getComponentType();

        if (arrayComponentType == null)
            throw new IllegalArgumentException(arrayType.getName() + " is not an array type");


        // Read size of array
        setLittleEndian(b);

        final int numElements = checkDeserializeArraySize(ReadInt(b));

        if (arrayComponentType.isPrimitive())
        {
            return ReadArrayOfPrimitives(b, arrayType, numElements);
        }
        else if (arrayComponentType.isAnnotationPresent(FStruct.class))
        {
            return ReadArrayOfObjects(b, arrayType, numElements);
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

    private static void setLittleEndian(final ByteBuffer b)
    {
        Objects.requireNonNull(b, "Buffer should not be null");

        b.order(ByteOrder.LITTLE_ENDIAN);
    }
}
