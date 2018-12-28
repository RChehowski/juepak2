package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.ECompressionFlags;
import eu.chakhouski.juepak.FPakCompressedBlock;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;
import eu.chakhouski.juepak.FPakInfo;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FMath;
import eu.chakhouski.juepak.ue4.FMemory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.util.Misc.BOOL;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class PakExtractor
{
    private static final int BLOCK_SIZE = 64 * 1024;

    /**
     * A statically-allocated entry, serves as check-entry to check pak-file integrity
     */
    private static final FPakEntry CheckEntry = new FPakEntry();

    /**
     * Key bytes must be nullified when decryption is done.
     */
    private static final byte[] SharedKeyBytes = new byte[32];

    /**
     * Inflater is being used to decompress ZLIB-compressed blocks
     */
    private static final Inflater inflater = new Inflater();

    //
    private static final ByteBuffer rawBuffer = ByteBuffer.allocate(BLOCK_SIZE);
    private static final ByteBuffer inflateBuffer = ByteBuffer.allocate(BLOCK_SIZE * 2);


    public synchronized static void Extract(FPakFile PakFile, FPakEntry Entry, WritableByteChannel ExtractionTarget)
            throws IOException
    {
        final FPakInfo PakInfo = PakFile.Info;
        final FileInputStream PakInputStream = PakFile.InputStream;

        // Might use cached channel if any has already created, this must be stable
        final FileChannel Source = PakInputStream.getChannel();

        // Deserialize header once again
        final long EntrySerializedSize = Entry.GetSerializedSize(PakInfo.Version);

        CheckEntry.Clean();
        CheckEntry.Deserialize(Source.map(MapMode.READ_ONLY, Entry.Offset, EntrySerializedSize), PakInfo.Version);

        // Compare entries
        if (!Entry.equals(CheckEntry))
        {
            throw new IllegalStateException(String.join(System.lineSeparator(), Arrays.asList(
                "Entry is invalid!",
                " > Index entry: " + Entry.toString(),
                " > Check entry: " + CheckEntry.toString()
            )));
        }

        if (Entry.CompressionMethod == ECompressionFlags.COMPRESS_None)
        {
            long Offset = Entry.Offset + EntrySerializedSize;
            long BytesRemaining = Entry.UncompressedSize;

            while (BytesRemaining > 0)
            {
                final long BytesToRead = FMath.Min(BLOCK_SIZE, BytesRemaining);

                ExtractBlock(Source, ExtractionTarget, Offset, (int)BytesToRead, BOOL(Entry.bEncrypted), false);

                Offset += BytesToRead;
                BytesRemaining -= BytesToRead;
            }
        }
        else if (Entry.CompressionMethod == ECompressionFlags.COMPRESS_ZLIB)
        {
            final FPakCompressedBlock[] CompressionBlocks = Entry.CompressionBlocks;
            final int NumCompressionBlocks = CompressionBlocks.length;

            for (int i = 0; i < NumCompressionBlocks; i++)
            {
                final FPakCompressedBlock Block = CompressionBlocks[i];

                final long Offset = Entry.Offset + Block.CompressedStart;
                final long Size = Block.CompressedEnd - Block.CompressedStart;

                ExtractBlock(Source, ExtractionTarget, Offset, (int) Size, BOOL(Entry.bEncrypted), true);
            }
        }
        else
        {
            throw new RuntimeException("Unsupported compression: " + ECompressionFlags.StaticToString(Entry.CompressionMethod));
        }
    }

    private static void ExtractBlock(SeekableByteChannel InChannel, WritableByteChannel OutChannel,
                                     final long BlockOffset, final int BlockSize,
                                     final boolean isEncrypted, final boolean isCompressed)
            throws IOException
    {
        // Check block size
        if (BlockSize < 0 || BlockSize > rawBuffer.capacity())
        {
            throw new IllegalArgumentException("Illegal block size: " + BlockSize + ", must be within 0.." + rawBuffer.capacity());
        }

        // Rewind buffer and set limit
        rawBuffer.position(0).limit(isEncrypted ? Align(BlockSize, FAES.AESBlockSize) : BlockSize);

        // Read data
        InChannel.position(BlockOffset);
        InChannel.read(rawBuffer);

        // Decrypt data if necessary
        if (isEncrypted)
        {
            // Get password bytes
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);

            // Decrypt data
            FAES.DecryptData(rawBuffer.array(), rawBuffer.limit(), SharedKeyBytes);

            // Erase password bytes
            FMemory.Memset(SharedKeyBytes, 0, sizeof(SharedKeyBytes));
        }

        // Decompress or just write
        if (isCompressed)
        {
            inflater.reset();
            inflater.setInput(rawBuffer.array(), 0, rawBuffer.limit());

            while (!inflater.finished())
            {
                try {
                    final int bytesInflated = inflater.inflate(inflateBuffer.array());
                    OutChannel.write((ByteBuffer) inflateBuffer.position(0).limit(bytesInflated));
                }
                catch (DataFormatException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        else
        {
            OutChannel.write((ByteBuffer) rawBuffer.position(0));
        }
    }
}
