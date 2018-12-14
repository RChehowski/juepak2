package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.UEPojo;

@UEPojo
public class FPakCompressedBlock
{
    /** Offset of the start of a compression block. Offset is absolute. */
    public long CompressedStart;
    /** Offset of the end of a compression block. This may not align completely with the start of the next block. Offset is absolute. */
    public long CompressedEnd;

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        FPakCompressedBlock B = (FPakCompressedBlock) o;
        return CompressedStart == B.CompressedStart && CompressedEnd == B.CompressedEnd;
    }
}
