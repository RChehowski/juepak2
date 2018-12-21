package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.annotations.FStruct;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;

public class UE4Deserializer
{
    public static String ReadString(ByteBuffer b)
    {
        // Ensure order is little endian
        b.order(ByteOrder.LITTLE_ENDIAN);

        int SaveNum = b.getInt();

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

    public static<T> T[] ReadArray(ByteBuffer b, Class<T> elementType)
    {
        b.order(ByteOrder.LITTLE_ENDIAN);

        if (!elementType.isAnnotationPresent(FStruct.class))
        {
            throw new IllegalArgumentException(join(lineSeparator(), asList(
                "Unsupported element class",
                "   Expected: Subclass of " + Objects.toString(FStruct.class),
                "   Actual: " + Objects.toString(elementType)
            )));
        }

        final int NumElements = ReadInt(b);

        @SuppressWarnings("unchecked")
        final T[] containingArray = (T[])Array.newInstance(elementType, NumElements);

        for (int i = 0; i < NumElements; i++)
        {
            containingArray[i] = ReflectDeserialize(b, elementType);
        }

        return containingArray;
    }

    private static<T> T ReflectDeserialize(ByteBuffer b, Class<T> elementType)
    {
        final Field[] declaredFields = elementType.getDeclaredFields();

        final T newInstance;
        try {
            newInstance = elementType.newInstance();
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < declaredFields.length; i++)
        {
            final Field f = declaredFields[i];

            try {
                final Class<?> fieldType = f.getType();

                if (fieldType == int.class)
                    f.setInt(newInstance, ReadInt(b));
                else if (fieldType == long.class)
                    f.setLong(newInstance, ReadLong(b));
                else
                    throw new RuntimeException("Unknown type: " + Objects.toString(fieldType));
            }
            catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return newInstance;
    }
}
