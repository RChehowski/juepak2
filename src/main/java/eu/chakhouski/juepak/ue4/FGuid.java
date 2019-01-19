package eu.chakhouski.juepak.ue4;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.JavaDecoratorMethod;
import eu.chakhouski.juepak.annotations.Operator;
import eu.chakhouski.juepak.util.UE4Deserializer;
import eu.chakhouski.juepak.util.UE4Deserializable;
import eu.chakhouski.juepak.util.UE4Serializable;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static eu.chakhouski.juepak.util.Misc.TEXT;

@FStruct
public class FGuid implements Comparable<FGuid>, UE4Serializable, UE4Deserializable
{
    /** Holds the first component. */
    private int A;

    /** Holds the second component. */
    private int B;

    /** Holds the third component. */
    private int C;

    /** Holds the fourth component. */
    private int D;

    public FGuid()
    {
        this(0, 0, 0, 0);
    }

    public FGuid(int a, int b, int c, int d)
    {
        A = a;
        B = b;
        C = c;
        D = d;
    }

    /**
     * Invalidates the GUID.
     *
     * see {@link #IsValid}
     */
    public final void Invalidate()
    {
        A = B = C = D = 0;
    }

    /**
     * Checks whether this GUID is valid or not.
     *
     * A GUID that has all its components set to zero is considered invalid.
     *
     * @return true if valid, false otherwise.
     * see {@link #Invalidate}
     */
    public final boolean IsValid()
    {
        return ((A | B | C | D) != 0);
    }

    /**
     * Compares two GUIDs for equality.
     *
     * @param X The first GUID to compare.
     * @param Y The second GUID to compare.
     * @return true if the GUIDs are equal, false otherwise.
     */
    @Operator("==")
    private static boolean operatorEQ(final FGuid X, final FGuid Y)
    {
        return ((X.A ^ Y.A) | (X.B ^ Y.B) | (X.C ^ Y.C) | (X.D ^ Y.D)) == 0;
    }

    /**
     * Compares two GUIDs for inequality.
     *
     * @param X The first GUID to compare.
     * @param Y The second GUID to compare.
     * @return true if the GUIDs are not equal, false otherwise.
     */
    @Operator("!=")
    private static boolean operatorNEQ(final FGuid X, final FGuid Y)
    {
        return ((X.A ^ Y.A) | (X.B ^ Y.B) | (X.C ^ Y.C) | (X.D ^ Y.D)) != 0;
    }

    /**
     * Compares two GUIDs.
     *
     * @param X The first GUID to compare.
     * @param Y The second GUID to compare.
     * @return true if the first GUID is less than the second one.
     */
    @Operator("<")
    @SuppressWarnings("SimplifiableConditionalExpression")
    private static boolean operatorLT(final FGuid X, final FGuid Y)
    {
        return	((X.A < Y.A) ? true : ((X.A > Y.A) ? false :
                ((X.B < Y.B) ? true : ((X.B > Y.B) ? false :
                ((X.C < Y.C) ? true : ((X.C > Y.C) ? false :
                ((X.D < Y.D) ? true : ((X.D > Y.D) ? false : false)))))))); //-V583
    }

    @Override
    @JavaDecoratorMethod
    public int compareTo(FGuid o)
    {
        if (operatorLT(this, o))
            return -1;
        else if (operatorLT(o, this))
            return 1;
        else
            return 0;
    }

    @Override
    @JavaDecoratorMethod
    public String toString()
    {
        return FString.Printf(TEXT("%08X%08X%08X%08X"), A, B, C, D);
    }

    @Override
    @JavaDecoratorMethod
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        return operatorEQ(this, (FGuid) o);
    }

    @Override
    @JavaDecoratorMethod
    public int hashCode()
    {
        final int[] array = {A, B, C, D};
        return Arrays.hashCode(array);
    }

    @Override
    public void Deserialize(ByteBuffer b)
    {
        A = UE4Deserializer.ReadInt(b);
        B = UE4Deserializer.ReadInt(b);
        C = UE4Deserializer.ReadInt(b);
        D = UE4Deserializer.ReadInt(b);
    }

    @Override
    public void Serialize(ByteBuffer b)
    {

    }
}
