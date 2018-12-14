package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.StaticSize;
import eu.chakhouski.juepak.annotations.UEPojo;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

import static eu.chakhouski.juepak.ECompressionFlags.COMPRESS_None;
import static eu.chakhouski.juepak.Sizeof.sizeof;

public class FPakEntry
{
    /** Offset into pak file where the file is stored.*/
    long Offset;
    /** Serialized file size. */
    long Size;
    /** Uncompressed file size. */
    long UncompressedSize;
    /** Compression method. */
    int CompressionMethod;
    /** File SHA1 value. */
    byte[] Hash = new byte[20];
    /** Array of compression blocks that describe how to decompress this pak entry. */
    List<FPakCompressedBlock> CompressionBlocks;
    /** Size of a compressed block in the file. */
    int CompressionBlockSize;
    /** True is file is encrypted. */
    byte bEncrypted;
    /** Flag is set to true when FileHeader has been checked against PakHeader. It is not serialized. */
    boolean Verified;

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
    long GetSerializedSize(int Version)
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
}