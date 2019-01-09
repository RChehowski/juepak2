package eu.chakhouski.juepak.pak.packing;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.util.PakCreator;

@FStruct
public class FPakInputPair
{
    public String Source;
    public String Dest;
    public long SuggestedOrder;
    public boolean bNeedsCompression;
    public boolean bNeedEncryption;

    public FPakInputPair()
    {
        SuggestedOrder = Long.MAX_VALUE;
        bNeedsCompression = false;
        bNeedEncryption = false;
    }

    public FPakInputPair(final String InSource, final String InDest)
    {
        Source = InSource;
        Dest = InDest;
        bNeedsCompression = false;
        bNeedEncryption = false;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        final FPakInputPair Other = (FPakInputPair) o;
        return Source.equals(Other.Source);
    }
}
