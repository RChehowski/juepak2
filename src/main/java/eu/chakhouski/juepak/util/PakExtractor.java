package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.ECompressionFlags;
import eu.chakhouski.juepak.pak.FPakCompressedBlock;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
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

import static eu.chakhouski.juepak.ECompressionFlags.StaticToString;
import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.util.Misc.BOOL;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class PakExtractor
{
    /**
     * A statically-allocated entry, serves as check-entry to check pak-file integrity
     */
    private static final FPakEntry CheckEntry = new FPakEntry();

    /**
     * Key bytes must be nullified when decryption is done.
     */
    private static final byte[] sharedKeyBytes = new byte[32];

    /**
     * Inflater is being used to decompress ZLIB-compressed blocks
     */
    private static final Inflater inflater = new Inflater();

    //
    private static final ByteBuffer srcBuffer = ByteBuffer.allocate(FPakInfo.MaxChunkDataSize);
    private static final ByteBuffer dstBuffer = ByteBuffer.allocate(FPakInfo.MaxChunkDataSize);


    public synchronized static void Extract(FPakFile PakFile, FPakEntry Entry, WritableByteChannel DestChannel)
            throws IOException
    {
        final FPakInfo PakInfo = PakFile.GetInfo();
        final FileInputStream PakInputStream = PakFile.InputStream;

        // Might use cached channel if any has already created, this must be stable
        final FileChannel SourceChannel = PakInputStream.getChannel();

        // Deserialize header once again
        final long EntrySerializedSize = Entry.GetSerializedSize(PakInfo.Version);

        CheckEntry.Clean();
        CheckEntry.Deserialize(SourceChannel.map(MapMode.READ_ONLY, Entry.Offset, EntrySerializedSize), PakInfo.Version);

        // Compare entries
        if (!Entry.equals(CheckEntry))
        {
            throw new IllegalStateException(String.join(System.lineSeparator(), Arrays.asList(
                "Entry is invalid!",
                " > Index entry: " + Entry.toString(),
                " > Check entry: " + CheckEntry.toString()
            )));
        }

        final boolean bEntryIsEncrypted = Entry.IsEncrypted();

        // Acquire key if entry is encrypted to save resources
        if (bEntryIsEncrypted)
        {
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(sharedKeyBytes);
        }

        // Choose compression method and extract
        switch (Entry.CompressionMethod)
        {
            case ECompressionFlags.COMPRESS_None:
            {
                long Offset = Entry.Offset + EntrySerializedSize;
                long BytesRemaining = Entry.UncompressedSize;

                while (BytesRemaining > 0)
                {
                    final long BytesToRead = Math.min(FPakInfo.MaxChunkDataSize, BytesRemaining);

                    ExtractBlock(SourceChannel, DestChannel, Offset, toInt(BytesToRead), bEntryIsEncrypted, false);

                    Offset += BytesToRead;
                    BytesRemaining -= BytesToRead;
                }
                break;
            }
            case ECompressionFlags.COMPRESS_ZLIB:
            {
                for (final FPakCompressedBlock Block : Entry.CompressionBlocks)
                {
                    final long GlobalOffset = BOOL(PakFile.GetInfo().HasRelativeCompressedChunkOffsets()) ? Entry.Offset : 0;
                    final long Offset = GlobalOffset + Block.CompressedStart;
                    final long BytesToRead = Block.CompressedEnd - Block.CompressedStart;

                    ExtractBlock(SourceChannel, DestChannel, Offset, (int) BytesToRead, bEntryIsEncrypted, true);
                }
                break;
            }
            default:
            {
                throw new IOException("Unsupported compression method: " + StaticToString(Entry.CompressionMethod));
            }
        }

        // Nullify key bytes if node was encrypted
        if (bEntryIsEncrypted)
        {
            FMemory.Memset(sharedKeyBytes, 0, sizeof(sharedKeyBytes));
        }
    }


    private static void ExtractBlock(SeekableByteChannel srcChannel, WritableByteChannel dstChannel,
                                     final long BlockOffset, final int BlockSize,
                                     final boolean isEncrypted, final boolean isCompressed)
            throws IOException
    {
        // Check block size
        if (BlockSize < 0 || BlockSize > srcBuffer.capacity())
        {
            throw new IOException("Illegal block size: " + BlockSize + ", must be within 0.." + srcBuffer.capacity());
        }

        // Rewind buffer and set limit
        srcBuffer.position(0);
        srcBuffer.limit(isEncrypted ? Align(BlockSize, FAES.getBlockSize()) : BlockSize);

        // Read data
        srcChannel.position(BlockOffset);
        srcChannel.read(srcBuffer);

        // Cache arrays to reduce
        final byte[] srcBufferArray = srcBuffer.array();
        final byte[] dstBufferArray = dstBuffer.array();

        // Decrypt data if necessary, sharedKeyBytes must be already acquired if entry is encrypted
        if (isEncrypted)
            FAES.DecryptData(srcBufferArray, srcBuffer.limit(), sharedKeyBytes);

        // Decompress or just write
        if (isCompressed)
        {
            inflater.reset();
            inflater.setInput(srcBufferArray, 0, srcBuffer.limit());

            // Read until inflater is finished
            while (!inflater.finished())
            {
                try {
                    final int bytesInflated = inflater.inflate(dstBufferArray);

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
            dstChannel.write((ByteBuffer) srcBuffer.position(0).limit(BlockSize));
        }
    }
}
