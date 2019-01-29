package com.vizor.unreal.pak;

import com.vizor.unreal.annotations.FStruct;
import com.vizor.unreal.annotations.JavaDecoratorMethod;
import com.vizor.unreal.util.UE4Deserializable;
import com.vizor.unreal.util.UE4Serializable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Struct storing offsets and sizes of a compressed block.
 */
@FStruct
public class FPakCompressedBlock implements UE4Serializable, UE4Deserializable
{
    /** Offset of the start of a compression block. Offset is absolute. */
    public long CompressedStart;
    /** Offset of the end of a compression block. This may not align completely with the start of the next block. Offset is absolute. */
    public long CompressedEnd;

    @SuppressWarnings("unused")
    public FPakCompressedBlock()
    {
        // Public no argument constructor for de-serializing purposes
    }

    @JavaDecoratorMethod
    public FPakCompressedBlock(long compressedStart, long compressedEnd)
    {
        CompressedStart = compressedStart;
        CompressedEnd = compressedEnd;
    }


    @Override
    @JavaDecoratorMethod
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        final FPakCompressedBlock other = (FPakCompressedBlock) o;

        return CompressedStart == other.CompressedStart && CompressedEnd == other.CompressedEnd;
    }

    @Override
    @JavaDecoratorMethod
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

    @Override
    @JavaDecoratorMethod
    public String toString()
    {
        return "FPakCompressedBlock{" +
            "CompressedStart=" + CompressedStart + ", " +
            "CompressedEnd=" + CompressedEnd +
        '}';
    }
}
