package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.pak.FPakCompressedBlock;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.ECompressionFlags;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.function.DoubleConsumer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.ue4.ECompressionFlags.StaticToString;
import static eu.chakhouski.juepak.util.Bool.BOOL;
import static eu.chakhouski.juepak.util.Misc.toInt;

public class PakExtractor
{
    /**
     * A statically-allocated entry, serves as check-entry to check pak-file integrity
     */
    private static final FPakEntry checkEntry = new FPakEntry();

    /**
     * Key bytes must be nullified when decryption is done.
     */
    private static final byte[] sharedKeyBytes = new byte[32];

    /**
     * Inflater is being used to decompress ZLIB-compressed blocks
     */
    private static final Inflater inflater = new Inflater();

    //
    private static final ByteBuffer srcBuffer = ByteBuffer.allocate(FPakInfo.MaxChunkDataSize * 2);
    private static final ByteBuffer dstBuffer = ByteBuffer.allocate(FPakInfo.MaxChunkDataSize * 2);


    public synchronized static void Extract(FPakFile PakFile, FPakEntry entry, WritableByteChannel DestChannel,
                                            DoubleConsumer progressConsumer) throws IOException
    {
        if (progressConsumer != null)
        {
            progressConsumer.accept(.0);
        }

        final FPakInfo PakInfo = PakFile.getInfo();
        final FileInputStream PakInputStream = PakFile.inputStream;

        // Might use cached channel if any has already created, this must be stable
        final FileChannel SourceChannel = PakInputStream.getChannel();

        // Deserialize header once again
        final long entrySerializedSize = entry.GetSerializedSize(PakInfo.Version);

        checkEntry.clean();
        checkEntry.Deserialize(SourceChannel.map(MapMode.READ_ONLY, entry.Offset, entrySerializedSize), PakInfo.Version);

        // Compare entries
        if (!entry.equals(checkEntry))
        {
            throw new IllegalStateException(String.join(System.lineSeparator(), Arrays.asList(
                "Entry is invalid!",
                " > Index entry: " + entry.toString(),
                " > Check entry: " + checkEntry.toString()
            )));
        }

        final boolean bEntryIsEncrypted = entry.IsEncrypted();

        // Acquire key if entry is encrypted to save resources
        if (bEntryIsEncrypted)
        {
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(sharedKeyBytes);
        }

        final long entrySize = entry.Size;

        // Choose compression method and extract
        switch (entry.CompressionMethod)
        {
            case ECompressionFlags.COMPRESS_None:
            {
                long Offset = entry.Offset + entrySerializedSize;
                long BytesRemaining = entrySize;

                while (BytesRemaining > 0)
                {
                    final long BytesToRead = Math.min(FPakInfo.MaxChunkDataSize, BytesRemaining);

                    ExtractBlock(SourceChannel, DestChannel, Offset, toInt(BytesToRead), bEntryIsEncrypted, false);

                    Offset += BytesToRead;
                    BytesRemaining -= BytesToRead;

                    // Report progress
                    if (progressConsumer != null)
                    {
                        progressConsumer.accept((double)(entrySize - BytesRemaining) / entrySize);
                    }
                }
                break;
            }
            case ECompressionFlags.COMPRESS_ZLIB:
            {
                long bytesProcessed = 0;

                for (final FPakCompressedBlock Block : entry.CompressionBlocks)
                {
                    final long GlobalOffset = BOOL(PakFile.getInfo().HasRelativeCompressedChunkOffsets()) ? entry.Offset : 0;
                    final long Offset = GlobalOffset + Block.CompressedStart;
                    final long BytesToRead = Block.CompressedEnd - Block.CompressedStart;

                    ExtractBlock(SourceChannel, DestChannel, Offset, (int) BytesToRead, bEntryIsEncrypted, true);

                    bytesProcessed += (Block.CompressedEnd - Block.CompressedStart);

                    // Report progress
                    if (progressConsumer != null)
                    {
                        progressConsumer.accept((double)bytesProcessed / entrySize);
                    }
                }
                break;
            }
            default:
            {
                throw new IOException("Unsupported compression method: " + StaticToString(entry.CompressionMethod));
            }
        }

        // Nullify key bytes if node was encrypted
        if (bEntryIsEncrypted)
        {
            Arrays.fill(sharedKeyBytes, (byte) 0);
        }
    }


    private static void ExtractBlock(SeekableByteChannel srcChannel, WritableByteChannel dstChannel,
                                     final long BlockOffset, final int blockSize,
                                     final boolean isEncrypted, final boolean isCompressed)
            throws IOException
    {
        // Check block size
        if (blockSize < 0 || blockSize > srcBuffer.capacity())
        {
            throw new IOException("Illegal block size: " + blockSize + ", must be within 0.." + srcBuffer.capacity());
        }

        // Rewind buffer and set limit
        srcBuffer.position(0);
        srcBuffer.limit(isEncrypted ? Align(blockSize, FAES.getBlockSize()) : blockSize);

        // Read data
        srcChannel.position(BlockOffset);
        srcChannel.read(srcBuffer);

        // Decrypt data if necessary, sharedKeyBytes must be already acquired if entry is encrypted
        if (isEncrypted)
        {
            FAES.DecryptData(srcBuffer.array(), srcBuffer.limit(), sharedKeyBytes);
        }

        // Decompress or just write
        if (isCompressed)
        {
            inflater.reset();
            inflater.setInput(srcBuffer.array(), 0, srcBuffer.limit());

            // Read until inflater is finished
            while (!inflater.finished())
            {
                try {
                    if (inflater.needsInput())
                    {
                        throw new IOException("Inflater is not ready to inflate");
                    }

                    final int bytesInflated = inflater.inflate(dstBuffer.array());

                    dstBuffer.position(0);
                    dstBuffer.limit(bytesInflated);

                    dstChannel.write(dstBuffer);
                }
                catch (DataFormatException e) {
                    throw new IOException(e);
                }
            }
        }
        else
        {
            dstChannel.write((ByteBuffer) srcBuffer.position(0).limit(blockSize));
        }
    }
}
