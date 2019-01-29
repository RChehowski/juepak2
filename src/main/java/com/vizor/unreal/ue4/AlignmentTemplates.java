package com.vizor.unreal.ue4;

public class AlignmentTemplates
{
    /**
     * Aligns a value to the nearest higher multiple of 'Alignment', which must be a power of two.
     * <long> version
     *
     * @param  Val        The value to align.
     * @param  Alignment The alignment value, must be a power of two.
     *
     * @return The value aligned up to the specified alignment.
     */
    public static long Align(/*T*/long Val, long Alignment)
    {
        return (Val + Alignment - 1) & -Alignment;
    }

    /**
     * Aligns a value to the nearest higher multiple of 'Alignment', which must be a power of two.
     * <int> version
     *
     * @param  Val        The value to align.
     * @param  Alignment The alignment value, must be a power of two.
     *
     * @return The value aligned up to the specified alignment.
     */
    public static int Align(/*T*/int Val, int Alignment)
    {
        return (Val + Alignment - 1) & -Alignment;
    }
}
