package eu.chakhouski.juepak.pak;

import eu.chakhouski.juepak.ue4.ECompressionFlags;
import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.JavaDecoratorField;
import eu.chakhouski.juepak.annotations.JavaDecoratorMethod;
import eu.chakhouski.juepak.annotations.Operator;
import eu.chakhouski.juepak.annotations.StaticSize;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FSHA1;
import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.UE4Deserializer;
import eu.chakhouski.juepak.util.UE4Serializer;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static eu.chakhouski.juepak.ue4.ECompressionFlags.COMPRESS_None;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

@FStruct
public class
FPakEntry
{
    @JavaDecoratorField
    private static final FPakCompressedBlock[] SharedDummyCompressionBlocks = new FPakCompressedBlock[0];


    private static final byte Flag_None = 0x00;
    private static final byte Flag_Encrypted = 0x01;
    private static final byte Flag_Deleted = 0x02;


    /** Offset into pak file where the file is stored.*/
    public long Offset;
    /** Serialized file size. */
    public long Size;
    /** Uncompressed file size. */
    public long UncompressedSize;
    /** Compression method. */
    public int CompressionMethod;
    /** File SHA1 value. */
    @StaticSize(20)
    public byte[] Hash = new byte[FSHA1.GetDigestLength()];
    /** Array of compression blocks that describe how to decompress this pak entry. */
    public FPakCompressedBlock[] CompressionBlocks;
    /** Size of a compressed block in the file. */
    public int CompressionBlockSize;
    /** Pak entry flags. */
    public byte Flags;
    /** Flag is set to true when FileHeader has been checked against PakHeader. It is not serialized. */
    public boolean Verified;

    /**
     * Constructor.
     */
    public FPakEntry()
    {
        Clean();
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
            SerializedSize += sizeof(Flags) + sizeof(CompressionBlockSize);
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
            // Read a dummy long (never use it further), just for compatibility reasons
            @SuppressWarnings("unused")
            final long Timestamp = UE4Deserializer.ReadLong(Ar);
        }

        Ar.get(Hash);

        if (Version >= FPakInfo.PakFile_Version_CompressionEncryption)
        {
            if (CompressionMethod != COMPRESS_None)
            {
                CompressionBlocks = UE4Deserializer.Read(Ar, FPakCompressedBlock[].class);
            }

            Flags = Ar.get();
            CompressionBlockSize = UE4Deserializer.ReadInt(Ar);
        }
    }

    public void Serialize(ByteBuffer Ar, int Version)
    {
        UE4Serializer.Write(Ar, Offset);
        UE4Serializer.Write(Ar, Size);
        UE4Serializer.Write(Ar, UncompressedSize);
        UE4Serializer.Write(Ar, CompressionMethod);
        UE4Serializer.Write(Ar, Hash);

        if (Version <= FPakInfo.PakFile_Version_Initial)
        {
            UE4Serializer.Write(Ar, (long)0);
        }

        if (Version >= FPakInfo.PakFile_Version_CompressionEncryption)
        {
            if (CompressionMethod != COMPRESS_None)
            {
                UE4Serializer.Write(Ar, CompressionBlocks);
            }

            UE4Serializer.Write(Ar, Flags);
            UE4Serializer.Write(Ar, CompressionBlockSize);
        }
    }

    void SetFlag( byte InFlag, boolean bValue )
    {
        if( bValue )
        {
            Flags |= InFlag;
        }
        else
        {
            Flags &= ~InFlag;
        }
    }

    boolean GetFlag( byte InFlag )
    {
        return (Flags & InFlag) == InFlag;
    }

    public boolean IsEncrypted()                 { return GetFlag(Flag_Encrypted); }
    public void SetEncrypted(boolean bEncrypted) { SetFlag( Flag_Encrypted, bEncrypted ); }

    public boolean IsDeleteRecord()                      { return GetFlag(Flag_Deleted); }
    public void SetDeleteRecord( boolean bDeleteRecord ) { SetFlag(Flag_Deleted, bDeleteRecord ); }

    @JavaDecoratorMethod
    public final FPakEntry Clean()
    {
        Offset = -1;
        Size = 0;
        UncompressedSize = 0;
        CompressionMethod = 0;
        CompressionBlocks = SharedDummyCompressionBlocks;
        CompressionBlockSize = 0;
        Flags = Flag_None;
        Verified = false;

        FMemory.Memset(Hash, 0, sizeof(Hash));
        return this;
    }

    /**
     * Compares two FPakEntry structs.
     */
    @Operator("==")
    @SuppressWarnings("WeakerAccess")
    public boolean operatorEQ (FPakEntry B)
    {
        // Offsets are not compared here because they're not
        // serialized with file headers anyway.
        return Size == B.Size &&
            UncompressedSize == B.UncompressedSize &&
            CompressionMethod == B.CompressionMethod &&
            Flags == B.Flags &&
            CompressionBlockSize == B.CompressionBlockSize &&
            FMemory.Memcmp(Hash, B.Hash, sizeof(Hash)) == 0 &&
            Arrays.deepEquals(CompressionBlocks, B.CompressionBlocks);
    }

    /**
     * Compares two FPakEntry structs.
     */
    @Operator("!=")
    @SuppressWarnings("unused")
    public boolean operatorNEQ (FPakEntry B)
    {
        // Offsets are not compared here because they're not
        // serialized with file headers anyway.
        return Size != B.Size ||
            UncompressedSize != B.UncompressedSize ||
            CompressionMethod != B.CompressionMethod ||
            Flags != B.Flags ||
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
        return "FPakEntry{" +
            "Offset=" + Offset +
            ", Size=" + Size +
            ", UncompressedSize=" + UncompressedSize +
            ", CompressionMethod=" + ECompressionFlags.StaticToString(CompressionMethod) +
            ", Hash=" + (Hash == null ? "null" : FString.BytesToHex(Hash)) +
            ", CompressionBlocks=" + (CompressionBlocks == null ? "null" : Arrays.asList(CompressionBlocks).toString()) +
            ", CompressionBlockSize=" + CompressionBlockSize +
            ", IsEncrypted=" + IsEncrypted() +
            ", IsDeleteRecord=" + IsDeleteRecord() +
        "}";
    }
}