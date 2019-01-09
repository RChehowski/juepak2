package eu.chakhouski.juepak.pak;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.Operator;
import eu.chakhouski.juepak.annotations.StaticSize;
import eu.chakhouski.juepak.ue4.FGuid;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.util.UE4Deserializer;
import eu.chakhouski.juepak.util.UE4Serializer;
import eu.chakhouski.juepak.util.UESerializable;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static eu.chakhouski.juepak.util.Misc.toByte;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

@FStruct
public class FPakInfo
{
    // enum
    // {
        /**
         * Magic number to use in header
         */
        public static int PakFile_Magic = 0x5A6F12E1;

        /**
         * Size of cached data
         */
        public static int MaxChunkDataSize = 64*1024;
    // }


    /** Version numbers. */
    //enum
    //{
    public static int
        PakFile_Version_Initial = 1,
        PakFile_Version_NoTimestamps = 2,
        PakFile_Version_CompressionEncryption = 3,
        PakFile_Version_IndexEncryption = 4,
        PakFile_Version_RelativeChunkOffsets = 5,
        PakFile_Version_DeleteRecords = 6,
        PakFile_Version_EncryptionKeyGuid = 7,


        PakFile_Version_Last = 8,
        PakFile_Version_Invalid = 9,
        PakFile_Version_Latest = PakFile_Version_Last - 1
    ;
    // }



    /** Pak file magic value. */
    public int Magic;
    /** Pak file version. */
    public int Version;
    /** Offset to pak file index. */
    public long IndexOffset;
    /** Size (in bytes) of pak file index. */
    public long IndexSize;
    /** Index SHA1 value. */
    @StaticSize(20)
    public byte[] IndexHash = new byte[20];
    /** Flag indicating if the pak index has been encrypted. */
    public byte bEncryptedIndex;
    /** Encryption key guid. Empty if we should use the embedded key. */
    public FGuid EncryptionKeyGuid = new FGuid();

    /**
     * Constructor.
     */
    public FPakInfo()
    {
		this.Magic = PakFile_Magic;
		this.Version = PakFile_Version_Latest;
		this.IndexOffset = -1;
		this.IndexSize = 0;
		this.bEncryptedIndex = 0;

        FMemory.Memset(IndexHash, 0, sizeof(IndexHash));
    }

    /**
     * Gets the size of data serialized by this struct.
     *
     * @return Serialized data size.
     */
    public long GetSerializedSize()
    {
        return GetSerializedSize(PakFile_Version_Latest);
    }

    /**
     * Gets the size of data serialized by this struct.
     *
     * @return Serialized data size.
     */
    public long GetSerializedSize(int InVersion)
    {
        long Size = sizeof(Magic) + sizeof(Version) + sizeof(IndexOffset) + sizeof(IndexSize) + sizeof(IndexHash) + sizeof(bEncryptedIndex);
        if (InVersion >= PakFile_Version_EncryptionKeyGuid) Size += sizeof(EncryptionKeyGuid);
        return Size;
    }

    /**
     */
    public long HasRelativeCompressedChunkOffsets()
    {
        return Version >= PakFile_Version_RelativeChunkOffsets ? 1 : 0;
    }

    void Deserialize(ByteBuffer b, int InVersion)
    {
        if (b.capacity() < (b.position() + GetSerializedSize(InVersion)))
        {
            Magic = 0;
            return;
        }

        if (InVersion >= PakFile_Version_EncryptionKeyGuid)
        {
            EncryptionKeyGuid.Deserialize(b);
        }

        bEncryptedIndex = UE4Deserializer.ReadByte(b);
        Magic = UE4Deserializer.ReadInt(b);
        Version = UE4Deserializer.ReadInt(b);
        IndexOffset = UE4Deserializer.ReadLong(b);
        IndexSize = UE4Deserializer.ReadLong(b);

        b.get(IndexHash);

        if (Version < PakFile_Version_IndexEncryption)
        {
            bEncryptedIndex = toByte(false);
        }

        if (Version < PakFile_Version_EncryptionKeyGuid)
        {
            EncryptionKeyGuid.Invalidate();
        }
    }

    public void Serialize(ByteBuffer b)
    {
        if (Version >= PakFile_Version_EncryptionKeyGuid)
        {
            EncryptionKeyGuid.Serialize(b);
        }

        // TODO: Not sure about it
        UE4Serializer.WriteByte(b, bEncryptedIndex);
        UE4Serializer.WriteInt(b, Magic);
        UE4Serializer.WriteInt(b, Version);
        UE4Serializer.WriteLong(b, IndexOffset);
        UE4Serializer.WriteLong(b, IndexSize);

        b.put(IndexHash);
    }

    @Operator("bool")
    public Boolean operatorBOOL()
    {
        return false;
    }

    @Override
    public String toString()
    {
        return "FPakInfo{" +
           "Magic=" + Magic +
           ", Version=" + Version +
           ", IndexOffset=" + IndexOffset +
           ", IndexSize=" + IndexSize +
           ", IndexHash=" + Arrays.toString(IndexHash) +
           ", bEncryptedIndex=" + bEncryptedIndex +
           '}';
    }
}
