package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.JavaDecoratorMethod;
import eu.chakhouski.juepak.annotations.Operator;
import eu.chakhouski.juepak.util.UEDeserializable;
import eu.chakhouski.juepak.util.UESerializable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Struct storing offsets and sizes of a compressed block.
 */
@FStruct
public class FPakCompressedBlock implements UESerializable, UEDeserializable
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


    // #region: decorator methods

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

    @Override
    public void Serialize(ByteBuffer b)
    {
        // Set order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Write fields
        b.putLong(CompressedStart);
        b.putLong(CompressedEnd);
    }

    @Override
    @JavaDecoratorMethod
    public void Deserialize(ByteBuffer b)
    {
        // Set order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Read fields
        CompressedStart = b.getLong();
        CompressedEnd = b.getLong();
    }
}
