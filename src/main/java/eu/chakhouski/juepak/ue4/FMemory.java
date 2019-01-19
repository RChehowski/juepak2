package eu.chakhouski.juepak.ue4;

import java.util.Arrays;

public class FMemory
{
    @SuppressWarnings("UnusedReturnValue")
    public static int Memcmp(byte[] a, byte[] b, int size)
    {
        int cmp = 0;

        for (int i = 0; (i < size) && (cmp == 0); i++)
            cmp = Byte.compare(a[i], b[i]);

        return cmp;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static byte[] Memset(byte[] Dest, int Int, int Count)
    {
        Arrays.fill(Dest, 0, Count, (byte)Int);
        return Dest;
    }
}
