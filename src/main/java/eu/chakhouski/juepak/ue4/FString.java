package eu.chakhouski.juepak.ue4;

import org.apache.commons.lang.mutable.MutableInt;

import java.util.Formatter;
import java.util.Locale;

import static eu.chakhouski.juepak.util.Misc.TCHAR;
import static eu.chakhouski.juepak.util.Misc.TEXT;

public class FString
{
    public static String Printf(String Fmt, Object... Params)
    {
        final Formatter Formatter = new Formatter();

        return Formatter.format(Locale.ENGLISH, Fmt, (Object[])Params).toString();
    }

    /** @return Char value of Nibble */
    public static char NibbleToTChar(byte Num)
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

    /**
     * Searches the string for the last occurrence of a character
     *
     * @param InString input string
     * @param InChar the character to search for
     * @param Index out the position the character was found at, INDEX_NONE if return is false
     * @return true if character was found in this string, otherwise false
     */
    public static boolean FindLastChar(String InString, char InChar, MutableInt Index)
    {
        final int Length = InString.length();

        for (int i = Length - 1; i >= 0; i--)
        {
            if (InString.charAt(i) == InChar)
            {
                Index.setValue(i);
                return true;
            }
        }

        Index.setValue(0);
        return false;
    }

    public static String Left(String Str, int Index)
    {
        return Str.substring(0, Index);
    }
}
