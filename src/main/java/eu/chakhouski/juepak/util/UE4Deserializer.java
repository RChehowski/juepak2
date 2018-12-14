package eu.chakhouski.juepak.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class UE4Deserializer
{
    public static String ReadString(ByteBuffer b)
    {
        // Ensure order is little endian
        b.order(ByteOrder.LITTLE_ENDIAN);

        final int numChars = b.getInt();

        final byte[] strBytes = new byte[numChars];
        b.get(strBytes);

        final String str = new String(strBytes, StandardCharsets.UTF_8);
        return str;
    }

    public static int ReadInt(ByteBuffer b)
    {
        // Ensure order is little endian
        b.order(ByteOrder.LITTLE_ENDIAN);

        return b.getInt();
    }
}
