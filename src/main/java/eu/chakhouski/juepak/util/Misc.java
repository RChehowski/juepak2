package eu.chakhouski.juepak.util;

import java.io.File;
import java.util.Collection;

@SuppressWarnings("SpellCheckingInspection")
public class Misc
{
    /**
     * Just a wrapper for java string making for us possible to keep the UE4 convention.
     *
     * @param s String to be converted to the text.
     * @return Actually, the same string.
     */
    public static String TEXT(String s)
    {
        return s;
    }

    public static char TEXT(char c)
    {
        return c;
    }

    public static char TCHAR(char c)
    {
        return c;
    }

    public static char TCHAR(int i)
    {
        return (char)i;
    }

    public static void checkf(Object expr, String format, Object... args)
    {
        if (!Bool.BOOL(expr))
        {
           throw new RuntimeException(String.format(format, (Object[]) args));
        }
    }

    /**
     * Converts a long value to int value, raising an exception if a long value was out of bounds.
     * Java does not allow an implicit conversion between long and int, explicit conversion simply looses precision.
     * UE4 has many implicit long-to-int casts
     *
     * @param value Long value to be converted to int.
     * @return Integer value.
     */
    public static int toInt(final long value)
    {
        if (value > (long)Integer.MAX_VALUE || value < (long)Integer.MIN_VALUE)
        {
            throw new IllegalArgumentException("Unable to cast " + value + " to int, it's out of integer bounds");
        }

        return (int)value;
    }

    /**
     * Converts a boolean to an integer (since java has to implicit boolean to int cast).
     *
     * @param value Value to be converted.
     * @return Integer value.
     */
    public static int toInt(final boolean value)
    {
        return value ? 1 : 0;
    }

    /**
     * Converts a boolean value into byte value.
     *
     * @param booleanValue A boolean value to be converted into byte.
     * @return Byte value.
     */
    public static byte toByte(final boolean booleanValue)
    {
        return booleanValue ? (byte)1 : (byte)0;
    }

    @SuppressWarnings("StringConcatenationInLoop")
    public static void deleteFiles(final Collection<File> tempFiles)
    {
        // Should NOT be replaced with StringBuilder because errors are unlikely,
        // So there's not reason to create an extra string builder for an error case.
        String deleteFailures = "";

        for (final File file : tempFiles)
        {
            if (file != null)
            {
                final boolean ableToDelete = file.delete();
                if (!ableToDelete)
                {
                    final String name = file.getName();

                    if (!deleteFailures.isEmpty())
                        deleteFailures += System.lineSeparator();

                    deleteFailures += " >\"" + name + "\"";
                }
            }
        }

        // Display message if some deleteFailures (delete deleteFailures are non-fatal?)
        System.err.println("Unable to delete following files:\n" + deleteFailures);
    }
}
