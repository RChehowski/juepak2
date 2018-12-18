package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.Operator;
import eu.chakhouski.juepak.annotations.StaticSize;
import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.ue4.FMemory;

import java.nio.ByteBuffer;

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
    public static int MaxChunkDataSize = 64*1024;


    /**
     * Version numbers.
     */
    public static int PakFile_Version_Initial = 1;
    public static int PakFile_Version_NoTimestamps = 2;
    public static int PakFile_Version_CompressionEncryption = 3;
    public static int PakFile_Version_IndexEncryption = 4;
    public static int PakFile_Version_RelativeChunkOffsets = 5;

    public static int PakFile_Version_Last = 6;
    public static int PakFile_Version_Latest = PakFile_Version_Last - 1;



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

        FMemory.Memset(IndexHash, (byte)0, sizeof(IndexHash));
    }

    /**
     * Gets the size of data serialized by this struct.
     *
     * @return Serialized data size.
     */
    public long GetSerializedSize()
    {
        return sizeof(Magic) + sizeof(Version) + sizeof(IndexOffset) + sizeof(IndexSize) + sizeof(IndexHash) + sizeof(bEncryptedIndex);
    }

    /**
     */
    long HasRelativeCompressedChunkOffsets()
    {
        return Version >= PakFile_Version_RelativeChunkOffsets ? 1 : 0;
    }

    void Deserialize(ByteBuffer b)
    {
        bEncryptedIndex = b.get();

        Magic = b.getInt();
        Version = b.getInt();
        IndexOffset = b.getLong();
        IndexSize = b.getLong();

        b.get(IndexHash);
    }

    @Operator("bool")
    public Boolean operatorBOOL()
    {
        return false;
    }
}
