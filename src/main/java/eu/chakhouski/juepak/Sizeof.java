package eu.chakhouski.juepak;

public class Sizeof
{
    public static int sizeof(boolean ignore) { return Byte.BYTES; }
    public static int sizeof(byte ignore) { return Byte.BYTES; }

    public static int sizeof(short ignore) { return Short.BYTES; }
    public static int sizeof(char ignore) { return Character.BYTES; }

    public static int sizeof(int ignore) { return Integer.BYTES; }
    public static int sizeof(long ignore) { return Long.BYTES; }

    public static int sizeof(float ignore) { return Float.BYTES; }
    public static int sizeof(double ignore) { return Double.BYTES; }



    public static int sizeof(boolean[] array) { return Byte.BYTES * array.length; }
    public static int sizeof(byte[] array) { return Byte.BYTES * array.length; }

    public static int sizeof(short[] array) { return Short.BYTES * array.length; }
    public static int sizeof(char[] array) { return Character.BYTES * array.length; }

    public static int sizeof(int[] array) { return Integer.BYTES * array.length; }
    public static int sizeof(long[] array) { return Long.BYTES * array.length; }

    public static int sizeof(float[] array) { return Float.BYTES * array.length; }
    public static int sizeof(double[] array) { return Double.BYTES * array.length; }
}
