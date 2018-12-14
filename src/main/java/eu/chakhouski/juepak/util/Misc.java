package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FFileIterator;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class Misc
{
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static Map<String, FPakEntry> GetSortedEntries(FPakFile PakFile, Comparator<FPakEntry> Comparator)
    {
        final TreeMap<FPakEntry, String> Map = new TreeMap<>(Comparator);

        // Sort map
        for (FFileIterator Iterator = PakFile.iterator(); Iterator.hasNext(); )
            Map.put(Iterator.next(), Iterator.Filename());

        final Map<String, FPakEntry> ResultMap = new LinkedHashMap<>();
        Map.forEach((k, v) -> ResultMap.put(v, k));

        return ResultMap;
    }
}
