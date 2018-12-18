package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.JavaDecoratorMethod;
import eu.chakhouski.juepak.annotations.Operator;
import eu.chakhouski.juepak.annotations.FStruct;

/**
 * Struct storing offsets and sizes of a compressed block.
 */
@FStruct
public class FPakCompressedBlock
{
    /** Offset of the start of a compression block. Offset is absolute. */
    public long CompressedStart;
    /** Offset of the end of a compression block. This may not align completely with the start of the next block. Offset is absolute. */
    public long CompressedEnd;

    @Operator("==")
    public boolean operatorEQ (FPakCompressedBlock B)
    {
        return CompressedStart == B.CompressedStart && CompressedEnd == B.CompressedEnd;
    }

    @Operator("!=")
    public boolean operatorNEQ (FPakCompressedBlock B)
    {
        return !(this.operatorEQ(B));
    }

    @Override
    @JavaDecoratorMethod
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        return this.operatorEQ((FPakCompressedBlock) o);
    }
}
