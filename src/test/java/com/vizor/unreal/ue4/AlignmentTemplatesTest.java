package com.vizor.unreal.ue4;

import org.testng.annotations.Test;

import static com.vizor.unreal.ue4.AlignmentTemplates.Align;
import static org.testng.Assert.assertEquals;

public class AlignmentTemplatesTest
{
    @Test
    public void AlignLongTest()
    {
        // Offsets are primes
        final long[] Offsets = {0, 1, 2, 3, 5, 7, 11, 13};

        // Alignment, used as stride as well
        final long Alignment = 16;

        for (long AnchorBound = Alignment; AnchorBound <= Alignment * 32000; AnchorBound += Alignment)
        {
            for (long Offset : Offsets)
                assertEquals(AnchorBound, Align(AnchorBound - Offset, Alignment));
        }
    }
}
