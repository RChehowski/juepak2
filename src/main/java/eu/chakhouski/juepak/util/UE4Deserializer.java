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
}
