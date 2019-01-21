package eu.chakhouski.juepak.ue4;

public class FMath
{
    public static long Min(long a, long b)
    {
        return Math.min(a, b);
    }

    public static int Min(int a, int b)
    {
        return Math.min(a, b);
    }


    public static long Max(long a, long b)
    {
        return Math.max(a, b);
    }

    public static int Max(int a, int b)
    {
        return Math.max(a, b);
    }


    public static int Clamp(final int X, final int Min, final int Max)
    {
        return X<Min ? Min : X<Max ? X : Max;
    }

    public static double Clamp(final double X, final double Min, final double Max)
    {
        return (X < Min) ? Min : (X < Max) ? X : Max;
    }
}
