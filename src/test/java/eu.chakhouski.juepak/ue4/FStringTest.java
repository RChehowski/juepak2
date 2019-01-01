package eu.chakhouski.juepak.ue4;

import org.junit.Assert;
import org.junit.Test;

public class FStringTest
{
    @Test
    public void StrnicmpTest()
    {
        final String a = "ABCDEFG";
        final String b = "ABCAEFG";

        // Compare a against a, must be the same
        Assert.assertEquals(+0, FString.Strnicmp(a, +0, a, +0, a.length()));

        // Compare first 3 symbols of a and b, must be different
        Assert.assertEquals(+0, FString.Strnicmp(a, +0, b, +0, 3));

        // Same as whole strings comparison, Must not overflow!
        Assert.assertEquals(0, FString.Strnicmp(a, +0, a, +0, a.length() * 999));


        // Compare whole strings, must be different
        Assert.assertEquals(+1, FString.Strnicmp(a, +0, b, +0, a.length()));
        Assert.assertEquals(-1, FString.Strnicmp(b, +0, a, +0, a.length()));

        // Compare first 4 letters, must be the same
        Assert.assertEquals(+1, FString.Strnicmp(a, +0, b, +0, 4));
        Assert.assertEquals(-1, FString.Strnicmp(b, +0, a, +0, 4));


        // Different and invalid lengths
        Assert.assertEquals(+1, FString.Strnicmp("B", 0, "AAAA", 0, 4));
        Assert.assertEquals(-1, FString.Strnicmp("A", 0, "AAAA", 0, 4));

    }
}
