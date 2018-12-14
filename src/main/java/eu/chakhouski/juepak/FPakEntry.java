package eu.chakhouski.juepak;

import eu.chakhouski.juepak.util.UE4Deserializer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static eu.chakhouski.juepak.ECompressionFlags.COMPRESS_None;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class FPakEntry
{
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
    public List<FPakCompressedBlock> CompressionBlocks;
    /** Size of a compressed block in the file. */
    public int CompressionBlockSize;
    /** True is file is encrypted. */
    public byte bEncrypted;
    /** Flag is set to true when FileHeader has been checked against PakHeader. It is not serialized. */
    public boolean Verified;

    /**
     * Constructor.
     */
    FPakEntry()
    {
        Offset = -1;
        Size = 0;
        UncompressedSize = 0;
        CompressionMethod = 0;
        CompressionBlockSize = 0;
        bEncrypted = 0;
        Verified = false;

        Arrays.fill(Hash, (byte)0);
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
            if(CompressionMethod != COMPRESS_None)
            {
                SerializedSize += sizeof(FPakCompressedBlock.class) * CompressionBlocks.size() + sizeof(int.class);
            }
        }
        if (Version < FPakInfo.PakFile_Version_NoTimestamps)
        {
            // Timestamp
            SerializedSize += sizeof(long.class);
        }
        return SerializedSize;
    }


    void Deserialize(ByteBuffer Ar, int Version)
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
                throw new IllegalStateException("No support for compressed blocks yet");

//                Ar << CompressionBlocks;
            }

            bEncrypted = Ar.get();
            CompressionBlockSize = UE4Deserializer.ReadInt(Ar);
        }
    }
}