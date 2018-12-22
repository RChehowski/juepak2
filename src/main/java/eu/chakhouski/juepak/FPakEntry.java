package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.JavaDecoratorField;
import eu.chakhouski.juepak.annotations.JavaDecoratorMethod;
import eu.chakhouski.juepak.annotations.Operator;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.UE4Deserializer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static eu.chakhouski.juepak.ECompressionFlags.COMPRESS_None;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class FPakEntry
{
    @JavaDecoratorField
    private static final FPakCompressedBlock[] SharedDummyCompressionBlocks = new FPakCompressedBlock[0];

    /** Offset into pak file where the file is stored.*/
    public long Offset;
    /** Serialized file size. */
    public long Size;
    /** Uncompressed file size. */
    public long UncompressedSize;
    /** Compression method. */
    public int CompressionMethod;
    /** File SHA1 value. */
    public byte[] Hash = new byte[20];
    /** Array of compression blocks that describe how to decompress this pak entry. */
    public FPakCompressedBlock[] CompressionBlocks = SharedDummyCompressionBlocks;
    /** Size of a compressed block in the file. */
    public int CompressionBlockSize;
    /** True is file is encrypted. */
    public byte bEncrypted;
    /** Flag is set to true when FileHeader has been checked against PakHeader. It is not serialized. */
    public boolean Verified;

    /**
     * Constructor.
     */
    public FPakEntry()
    {
        Offset = -1;
        Size = 0;
        UncompressedSize = 0;
        CompressionMethod = 0;
        CompressionBlockSize = 0;
        bEncrypted = 0;
        Verified = false;

        FMemory.Memset(Hash, 0, sizeof(Hash));
    }

    /**
     * Gets the size of data serialized by this struct.
     *
     * @return Serialized data size.
     */
    public long GetSerializedSize(int Version)
    {
        long SerializedSize = sizeof(Offset) + sizeof(Size) + sizeof(UncompressedSize) + sizeof(CompressionMethod) + sizeof(Hash);
        if (Version >= FPakInfo.PakFile_Version_CompressionEncryption)
        {
            SerializedSize += sizeof(bEncrypted) + sizeof(CompressionBlockSize);
            if (CompressionMethod != COMPRESS_None)
            {
                SerializedSize +=
                    /* array items */ sizeof(FPakCompressedBlock.class) * CompressionBlocks.length +
                    /* array size  */ sizeof(int.class);
            }
        }
        if (Version < FPakInfo.PakFile_Version_NoTimestamps)
        {
            // Timestamp
            SerializedSize += sizeof(long.class);
        }
        return SerializedSize;
    }


    public void Deserialize(ByteBuffer Ar, int Version)
    {
        Offset = UE4Deserializer.ReadLong(Ar);
        Size = UE4Deserializer.ReadLong(Ar);
        UncompressedSize = UE4Deserializer.ReadLong(Ar);
        CompressionMethod = UE4Deserializer.ReadInt(Ar);


        if (Version <= FPakInfo.PakFile_Version_Initial)
        {
            throw new IllegalStateException("Too old pak revision");

//            FDateTime Timestamp;
//            Ar << Timestamp;
        }

        Ar.get(Hash);

        if (Version >= FPakInfo.PakFile_Version_CompressionEncryption)
        {
            if (CompressionMethod != COMPRESS_None)
            {
                CompressionBlocks = UE4Deserializer.ReadStructArray(Ar, FPakCompressedBlock.class);
            }

            bEncrypted = Ar.get();
            CompressionBlockSize = UE4Deserializer.ReadInt(Ar);
        }
    }

    /**
     * Compares two FPakEntry structs.
     */
    @Operator("==")
    @SuppressWarnings({"WeakerAccess"})
    public boolean operatorEQ (FPakEntry B)
    {
        // Offsets are not compared here because they're not
        // serialized with file headers anyway.
        return Size == B.Size &&
            UncompressedSize == B.UncompressedSize &&
            CompressionMethod == B.CompressionMethod &&
            bEncrypted == B.bEncrypted &&
            CompressionBlockSize == B.CompressionBlockSize &&
            FMemory.Memcmp(Hash, B.Hash, sizeof(Hash)) == 0 &&
            Arrays.deepEquals(CompressionBlocks, B.CompressionBlocks);
    }

    /**
     * Compares two FPakEntry structs.
     */
    @Operator("!=")
    @SuppressWarnings({"unused"})
    public boolean operatorNEQ (FPakEntry B)
    {
        // Offsets are not compared here because they're not
        // serialized with file headers anyway.
        return Size != B.Size ||
            UncompressedSize != B.UncompressedSize ||
            CompressionMethod != B.CompressionMethod ||
            bEncrypted != B.bEncrypted ||
            CompressionBlockSize != B.CompressionBlockSize ||
            FMemory.Memcmp(Hash, B.Hash, sizeof(Hash)) != 0 ||
            !Arrays.deepEquals(CompressionBlocks, B.CompressionBlocks);
    }


    @Override
    @JavaDecoratorMethod
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        return operatorEQ((FPakEntry) o);
    }

    @Override
    @JavaDecoratorMethod
    public String toString()
    {
        final StringBuffer sb = new StringBuffer("FPakEntry{");

//        sb.append("Offset=").append(Offset);
        sb.append("Size=").append(Size);
        sb.append(", UncompressedSize=").append(UncompressedSize);
        sb.append(", CompressionMethod=").append(CompressionMethod);
        sb.append(", Hash=");
        sb.append(Hash == null ? "null" : FString.BytesToHex(Hash));
        sb.append(", CompressionBlocks=").append(CompressionBlocks == null ? "null" : Arrays.asList(CompressionBlocks).toString());
        sb.append(", CompressionBlockSize=").append(CompressionBlockSize);
        sb.append(", bEncrypted=").append(bEncrypted);
//        sb.append(", Verified=").append(Verified);
        sb.append('}');

        return sb.toString();
    }
}