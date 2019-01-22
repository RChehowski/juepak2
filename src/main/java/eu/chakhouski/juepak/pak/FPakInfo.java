package eu.chakhouski.juepak.pak;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.StaticSize;
import eu.chakhouski.juepak.ue4.FGuid;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FSHA1;
import eu.chakhouski.juepak.util.UE4Deserializer;
import eu.chakhouski.juepak.util.UE4Serializer;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static eu.chakhouski.juepak.util.Misc.toByte;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

@FStruct
public class FPakInfo
{
    /**
     * Magic number to use in header
     */
    public static int PakFile_Magic = 0x5A6F12E1;

    /**
     * Size of cached data
     */
    public static int MaxChunkDataSize = 64 * 1024;

    /** Version numbers. */
    @SuppressWarnings({"unused", "WeakerAccess", "RedundantSuppression"})
    public static final int
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

    public static String pakFileVersionToString(int version)
    {
        switch (version)
        {
            case PakFile_Version_Initial:
                return "PakFile_Version_Initial";
            case PakFile_Version_NoTimestamps:
                return "PakFile_Version_NoTimestamps";
            case PakFile_Version_CompressionEncryption:
                return "PakFile_Version_CompressionEncryption";
            case PakFile_Version_IndexEncryption:
                return "PakFile_Version_IndexEncryption";
            case PakFile_Version_RelativeChunkOffsets:
                return "PakFile_Version_RelativeChunkOffsets";
            case PakFile_Version_DeleteRecords:
                return "PakFile_Version_DeleteRecords";
            case PakFile_Version_EncryptionKeyGuid:
                return "PakFile_Version_EncryptionKeyGuid";


            case PakFile_Version_Last:
                return "PakFile_Version_Last";
            case PakFile_Version_Invalid:
                return "PakFile_Version_Invalid";
        }

        return "Unknown";
    }

//    public static int getPakVersionForEngine(String engineVersion)
//    {
//        final String[] split = engineVersion.split("\\.");
//
//        if (split.length < 2)
//            throw new IllegalArgumentException("Insufficient version number count: " + split.length);
//
//        if (split.length > 3)
//            throw new IllegalArgumentException("Version number overflow: " + split.length);
//
//        // Extract major, min and patch versions
//        final int maj = Integer.valueOf(split[0]);
//        final int min = Integer.valueOf(split[1]);
//        final int ptc = (split.length == 3) ? Integer.valueOf(split[2]) : 0;
//
//        // Restrict negative and invalid items
//        if (maj != 4)
//            throw new IllegalArgumentException("Only UE4 is supported, given: " + "[" + maj + "]." + min + "." + ptc);
//
//        if (min < 0)
//            throw new IllegalArgumentException("Min version is negative, given: " + maj + ".[" + min + "]." + ptc);
//
//        if (ptc < 0)
//            throw new IllegalArgumentException("Patch version is negative, given: " + maj + "." + min + ".[" + ptc + "]");
//
//        // Select version according to https://github.com/EpicGames/UnrealEngine/blob/master/Engine/Source/Runtime/PakFile/Public/IPlatformFilePak.h
//        if (min < 3)
//            return PakFile_Version_NoTimestamps;
//        else if (min < 16)
//            return PakFile_Version_CompressionEncryption;
//        else if (min < 20)
//            return PakFile_Version_IndexEncryption;
//        else if (min < 21)
//            return PakFile_Version_RelativeChunkOffsets;
//        else
//            return PakFile_Version_Latest;
//    }


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
    public byte[] IndexHash = new byte[FSHA1.GetDigestLength()];
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
		//this.bEncryptedIndex = 0;

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

        if (InVersion >= PakFile_Version_EncryptionKeyGuid)
        {
            Size += sizeof(EncryptionKeyGuid);
        }

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

        UE4Serializer.Write(b, bEncryptedIndex);
        UE4Serializer.Write(b, Magic);
        UE4Serializer.Write(b, Version);
        UE4Serializer.Write(b, IndexOffset);
        UE4Serializer.Write(b, IndexSize);
        UE4Serializer.Write(b, IndexHash);
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
