package eu.chakhouski.juepak.ue4;

import java.util.Formatter;
import java.util.Locale;

public class FString
{

    public static String Printf(String Fmt, Object... Params)
    {
        final Formatter Formatter = new Formatter();

        return Formatter.format(Locale.ENGLISH, Fmt, (Object[])Params).toString();
    }
}
