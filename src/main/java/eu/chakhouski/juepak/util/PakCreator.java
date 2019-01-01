package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.ECompressionFlags;
import eu.chakhouski.juepak.FPakCompressedBlock;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakInfo;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FMemory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.ue4.AlignmentTemplates.AlignDown;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class PakCreator
{
    private final int pakVersion;

    /**
     * Deflater instance, may be lazily created via {@link #getDeflater()}
     */
    private Deflater deflater;

    /**
     * Key bytes must be nullified when decryption is done.
     */
    private static final byte[] SharedKeyBytes = new byte[32];


    private ByteBuffer deflateSrcBuffer;
    private ByteBuffer deflateDstBuffer;



    public PakCreator(int pakVersion)
    {
        this.pakVersion = pakVersion;
    }

    public FPakEntry deflateFile(InputStream is, FileChannel os, boolean bEncrypt, int MaxCompressionBlockSize)
            throws IOException
    {
        // Store initial position to write here a FPakEntry instance a bit later
        final long dataOffset = os.position();

        // The Deflater and buffers are shared withing this PakCreator
        // ByteBuffers are being allocated for
        final Deflater deflater = getDeflater();
        final ByteBuffer deflateSrcBuffer = getDeflateSrcBuffer(MaxCompressionBlockSize);
        final ByteBuffer deflateDstBuffer = getDeflateDstBuffer(MaxCompressionBlockSize);

        final List<FPakCompressedBlock> compressionBlocks = new ArrayList<>();

        // Acquire key if entry is encrypted to save resources
        if (bEncrypt)
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);

        // Initialize counters
        long uncompressedSize = 0;
        long size = 0;
        int compressionBlockSize = 0;

        int bytesRead;
        while ((bytesRead = is.read(deflateSrcBuffer.array(), 0, deflateSrcBuffer.capacity())) > 0)
        {
            deflater.reset();
            deflater.setInput(deflateSrcBuffer.array(), 0, bytesRead);
            deflater.finish();

            // 'bytesWritten' is a size of deflated block data
            int bytesWritten = 0;

            // deflate until finished
            while (!deflater.finished())
            {
                // Deflate data
                final int numBytesDeflated = deflater.deflate(deflateDstBuffer.array(), 0, deflateDstBuffer.capacity());

                // Remember compressed block start {
                final FPakCompressedBlock compressedBlock = new FPakCompressedBlock();
                compressedBlock.CompressedStart = os.position();

                // Encrypt data if entry data should be encrypted
                final int numBytesToWrite;
                if (bEncrypt)
                {
                    numBytesToWrite = Align(numBytesDeflated, FAES.AESBlockSize);
                    FAES.EncryptData(deflateDstBuffer.array(), numBytesToWrite, SharedKeyBytes);
                }
                else
                {
                    numBytesToWrite = numBytesDeflated;
                }

                // Limit the buffer with numBytesDeflated, and finally, write data
                os.write((ByteBuffer)deflateDstBuffer.position(0).limit(numBytesToWrite));
                bytesWritten += numBytesToWrite;

                // Remember compressed block end }
                compressedBlock.CompressedEnd = os.position();
                compressionBlocks.add(compressedBlock);
            }

            // Perform overflow check
            if (bytesWritten > MaxCompressionBlockSize)
            {
                throw new IllegalStateException("Deflate error: Block size mustn't be larger than MaxCompressionBlockSize " +
                    MaxCompressionBlockSize + ", but got " + bytesWritten);
            }

            // Update counters
            uncompressedSize += bytesRead;
            size += bytesWritten;

            // Update compression block size
            compressionBlockSize = Math.max(compressionBlockSize, bytesWritten);
        }

        // Nullify key bytes if node was encrypted
        if (bEncrypt)
            FMemory.Memset(SharedKeyBytes, 0, sizeof(SharedKeyBytes));

        // Finally, setup a PakInfo here
        final FPakEntry entry = new FPakEntry();
        entry.Offset = dataOffset;
        entry.UncompressedSize = uncompressedSize;
        entry.Size = size;
        entry.CompressionMethod = ECompressionFlags.COMPRESS_ZLIB;
        entry.CompressionBlocks = compressionBlocks.toArray(new FPakCompressedBlock[0]);
        entry.CompressionBlockSize = compressionBlockSize;

        // Set flags
        entry.SetEncrypted(bEncrypt);
        entry.SetDeleteRecord(false);

        // Calculate relative offset
        final long entrySerializedSize = entry.GetSerializedSize(pakVersion);
        final long offset = (pakVersion < FPakInfo.PakFile_Version_RelativeChunkOffsets) ? 0 : dataOffset;

        for (final FPakCompressedBlock block : entry.CompressionBlocks)
            block.relativize(offset - entrySerializedSize);

        // Byte buffer
        final ByteBuffer entryBuffer = ByteBuffer.allocate(toInt(entrySerializedSize)).order(ByteOrder.LITTLE_ENDIAN);
        entry.Serialize(entryBuffer, pakVersion);

        // Write entry
        os.position(dataOffset).write((ByteBuffer)entryBuffer.position(0));

        // Set position to an end
        os.position(os.size());

        return entry;
    }



    // lazy deflater
    private Deflater getDeflater()
    {
        if (deflater == null)
        {
            deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        }

        return deflater;
    }

    private ByteBuffer getDeflateSrcBuffer(int n)
    {
        if (deflateSrcBuffer == null)
        {
            assert n % FAES.AESBlockSize == 0;

            // @see https://stackoverflow.com/questions/23571387/whats-the-most-that-gzip-or-deflate-can-increase-a-file-size
            final long l = (long) (n + 5 * (Math.floor((double) n / 16383.0) + 1));

            // align size
            final long srcBufferSize = AlignDown(n - (l - n), FAES.AESBlockSize);

            deflateSrcBuffer = ByteBuffer.allocate(toInt(srcBufferSize));
        }

        return deflateSrcBuffer;
    }

    public ByteBuffer getDeflateDstBuffer(int n)
    {
        if (deflateDstBuffer == null)
        {
            assert n % FAES.AESBlockSize == 0;

            deflateDstBuffer = ByteBuffer.allocate(n);
        }

        return deflateDstBuffer;
    }
}
