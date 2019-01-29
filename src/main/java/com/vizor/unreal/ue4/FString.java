package com.vizor.unreal.ue4;

import static com.vizor.unreal.util.Misc.TCHAR;
import static com.vizor.unreal.util.Misc.TEXT;

public class FString
{
    @SuppressWarnings("WeakerAccess")
    public static String Printf(String Fmt, Object... Params)
    {
        return String.format(Fmt, Params);
    }

    /** @return Char value of Nibble */
    private static char NibbleToTChar(byte Num)
    {
        final int IntNum = Num & 0xF;

        if ((Num & 0xF) > 9)
        {
            return (char)(TEXT('A') + TCHAR(IntNum - 10));
        }
        return (char)(TEXT('0') + TCHAR(IntNum));
    }

    /**
     * Convert a byte to hex
     * @param In byte value to convert
     *
     * @return Result out hex value output
     */
    @SuppressWarnings("WeakerAccess")
    public static String ByteToHex(byte In)
    {
        String Result = "";

        Result += NibbleToTChar((byte)(In >> 4));
        Result += NibbleToTChar((byte)(In & 15));

        return Result;
    }


    /**
     * Convert an array of bytes to hex
     * @param In byte array values to convert
     * @param Count number of bytes to convert
     * @return Hex value in string.
     */
    @SuppressWarnings("WeakerAccess")
    public static String BytesToHex(final byte[] In, final int Count)
    {
        final StringBuilder Result = new StringBuilder();

        for (int i = 0; i < Count; i++)
        {
            Result.append(ByteToHex(In[i]));
        }

        return Result.toString();
    }

    /**
     * Convert an array of bytes to hex
     * @param In byte array values to convert
     * @return Hex value in string.
     */
    public static String BytesToHex(final byte[] In)
    {
        return BytesToHex(In, In.length);
    }
}
