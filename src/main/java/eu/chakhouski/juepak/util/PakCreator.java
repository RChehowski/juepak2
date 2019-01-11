package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.ECompressionFlags;
import eu.chakhouski.juepak.pak.FPakCompressedBlock;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FMemory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.ue4.AlignmentTemplates.AlignDown;
import static eu.chakhouski.juepak.util.Misc.toByte;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class PakCreator implements AutoCloseable
{
    private final int pakVersion;
    private final String savePath;


    private final FileOutputStream pakStream;
    private final FileChannel pakChannel;

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

    private ByteBuffer pakEntryBuffer;



    private Map<Path, FPakEntry> entries = new HashMap<>();

    private static final MessageDigest Sha1;

    static
    {
        try {
            Sha1 = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public PakCreator(String savePath, int pakVersion) throws FileNotFoundException
    {
        this.savePath = savePath;
        this.pakVersion = pakVersion;

        pakStream = new FileOutputStream(savePath);
        pakChannel = pakStream.getChannel();
    }

    @Override
    public void close() throws Exception
    {
//        final FPakInfo pakInfo = createIndex(false, entries, pakChannel);
//
//        final ByteBuffer b = ByteBuffer.allocate(toInt(pakInfo.GetSerializedSize(pakVersion)));
//        pakInfo.Serialize(b);
//
//        b.flip();
//        pakChannel.write(b);
//
//        // Close stream
//        pakStream.closeAndWrite();
    }

    public final void addFile(Path path) throws IOException
    {
        final int maxCompressionBlockSize = 64 * 1024;

        try (final FileInputStream fis = new FileInputStream(path.toFile()))
        {
            final FPakEntry e = deflateFile(fis, pakChannel, true, maxCompressionBlockSize);
//            entries.add(e);
        }
    }

    private FPakEntry deflateFile(InputStream is, FileChannel os, boolean bEncrypt, int MaxCompressionBlockSize)
            throws IOException
    {
        // Store initial position to write here a FPakEntry instance a bit later
        final long dataOffset = os.position();

        // The instance and buffers are shared withing this PakCreator
        final Deflater deflater = getDeflater();
        final ByteBuffer deflateSrcBuffer = getDeflateSrcBuffer(MaxCompressionBlockSize);
        final ByteBuffer deflateDstBuffer = getDeflateDstBuffer(MaxCompressionBlockSize);

        final List<FPakCompressedBlock> blocks = new ArrayList<>();

        // Acquire key if entry is encrypted to save resources
        if (bEncrypt)
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);

        // Initialize counters
        long uncompressedSize = 0;
        long size = 0;
        int compressionBlockSize = 0;

        Sha1.reset();

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
                    numBytesToWrite = Align(numBytesDeflated, FAES.getBlockSize());
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
                blocks.add(compressedBlock);
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

            // Update sha
            Sha1.update(deflateSrcBuffer.array(), 0, bytesRead);
        }

        // Nullify key bytes if node was encrypted
        if (bEncrypt)
            FMemory.Memset(SharedKeyBytes, 0, sizeof(SharedKeyBytes));

        // Finally, setup a PakInfo here
        final FPakEntry entry = new FPakEntry();
        {
            entry.Offset = dataOffset;
            entry.UncompressedSize = uncompressedSize;
            entry.Size = size;
            entry.Hash = Sha1.digest();
            entry.CompressionMethod = ECompressionFlags.COMPRESS_ZLIB;
            entry.CompressionBlocks = blocks.toArray(new FPakCompressedBlock[0]);
            entry.CompressionBlockSize = compressionBlockSize;
        }

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

    private FPakInfo createIndex(boolean encryptIndex, Collection<FPakEntry> pakEntries, final FileChannel channel) throws IOException
    {
        final long indexBeginOffset = channel.position();

        Sha1.reset();

        // TODO: Replace mock data
        final ByteBuffer map = ByteBuffer.allocate(100);
        UE4Serializer.Write(map, "../../../");
        UE4Serializer.Write(map, pakEntries.size());
        map.flip();
        Sha1.update(map.array(), 0, map.limit());


        channel.write(map);

        // Acquire key if entry is encrypted to save resources
        if (encryptIndex)
        {
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);
        }

        // Allocate initial buffer (it might be re-allocated during algorithm progression)
        ByteBuffer buffer = ByteBuffer.allocate(1024 * FAES.getBlockSize()).order(ByteOrder.LITTLE_ENDIAN);

        FPakEntry cachedPakEntry = null;
        for (Iterator<FPakEntry> iterator = pakEntries.iterator(); iterator.hasNext(); )
        {
            buffer.rewind().limit(buffer.capacity());

            int numSerializedEntriesPerLoop = 0;
            while ((cachedPakEntry != null) || iterator.hasNext())
            {
                // take if not taken yet
                if (cachedPakEntry == null)
                {
                    cachedPakEntry = iterator.next();
                }

                final long serializedSize = cachedPakEntry.GetSerializedSize(pakVersion);
                if (buffer.position() + serializedSize >= buffer.capacity())
                {
                    if (numSerializedEntriesPerLoop == 0)
                    {
                        final int newCap = encryptIndex ? Align(toInt(serializedSize), FAES.getBlockSize()) : toInt(serializedSize);

                        System.err.println("Insufficient buffer capacity to fit " + serializedSize + " bytes: " + buffer.capacity()
                            + " new: " + newCap + (encryptIndex ? " (aligned by " + FAES.getBlockSize() + ")" : ""));

                        buffer = ByteBuffer.allocate(newCap).order(ByteOrder.LITTLE_ENDIAN);
                    }

                    break;
                }

                // serialize and discard entry
                cachedPakEntry.Serialize(buffer, pakVersion);
                cachedPakEntry = null;

                // entry successfully serialized into the buffer
                ++numSerializedEntriesPerLoop;
            }

            // Flip buffer
            buffer.flip();

            // Encrypt part if it should be encrypted
            if (encryptIndex)
            {
                final int alignedPartSize = Align(buffer.limit(), FAES.getBlockSize());

                // Check capacity
                if (alignedPartSize > buffer.capacity())
                {
                    final ByteBuffer newBuffer = ByteBuffer.allocate(alignedPartSize).order(ByteOrder.LITTLE_ENDIAN);
                    newBuffer.put(buffer);
                    newBuffer.position(0).limit(alignedPartSize);

                    buffer = newBuffer;
                }

                // Limit with aligned size
                buffer.limit(alignedPartSize);

                // Encrypt data
                FAES.EncryptData(buffer.array(), buffer.limit(), SharedKeyBytes);
            }

            // Update sha1
            Sha1.update(buffer.array(), 0, buffer.limit());

            // Write data to channel
            channel.write(buffer);
        }

        final long indexEndOffset = channel.position();

        final FPakInfo pi = new FPakInfo();
        {
            pi.Magic = FPakInfo.PakFile_Magic;
            pi.Version = pakVersion;
            pi.IndexOffset = indexBeginOffset;
            pi.IndexSize = indexEndOffset - indexBeginOffset;
            pi.IndexHash = Sha1.digest();
            pi.bEncryptedIndex = toByte(encryptIndex);
        }

        return pi;
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
            assert n % FAES.getBlockSize() == 0;

            // @see https://stackoverflow.com/questions/23571387/whats-the-most-that-gzip-or-deflate-can-increase-a-file-size
            final long l = (long) (n + 5 * (Math.floor((double) n / 16383.0) + 1));

            // align size
            final long srcBufferSize = AlignDown(n - (l - n), FAES.getBlockSize());

            deflateSrcBuffer = ByteBuffer.allocate(toInt(srcBufferSize));
        }

        return deflateSrcBuffer;
    }

    private ByteBuffer getDeflateDstBuffer(int n)
    {
        if (deflateDstBuffer == null)
        {
            assert n % FAES.getBlockSize() == 0;

            deflateDstBuffer = ByteBuffer.allocate(n);
        }

        return deflateDstBuffer;
    }

    public void finishWrite()
    {

    }

//    private void writePakEntry(final FPakEntry pakEntry, final WritableByteChannel channel) throws IOException
//    {
//        final int serializedSize = toInt(pakEntry.GetSerializedSize(pakVersion));
//        if ((pakEntryBuffer == null) || (pakEntryBuffer.capacity() < serializedSize))
//        {
//            // Let's align by 256 to reduce scattering
//            pakEntryBuffer = ByteBuffer.allocate(Align(serializedSize, 256));
//
//            // Endian should be little endian
//            pakEntryBuffer.order(ByteOrder.LITTLE_ENDIAN);
//        }
//
//        // Serialize pak entry
//        pakEntryBuffer.rewind().limit(pakEntryBuffer.capacity());
//        pakEntry.Serialize(pakEntryBuffer, pakVersion);
//
//        // Finally write to the channel
//        pakEntryBuffer.flip();
//        channel.write(pakEntryBuffer);
//    }

//    private static String GetLongestPath(List<FPakInputPair> FilesToAdd)
//    {
//        String LongestPath = "";
//        int MaxNumDirectories = 0;
//
//        for (final FPakInputPair FileToAdd : FilesToAdd)
//        {
//            final String Filename = FileToAdd.Dest;
//
//            int NumDirectories = 0;
//            for (int Index = 0; Index < Filename.length(); Index++)
//            {
//                if (Filename.charAt(Index) == '/')
//                {
//                    NumDirectories++;
//                }
//            }
//            if (NumDirectories > MaxNumDirectories)
//            {
//                LongestPath = Filename;
//                MaxNumDirectories = NumDirectories;
//            }
//        }
//        return FPaths.GetPath(LongestPath) + TEXT("/");
//    }
//
//    public static String GetCommonRootPath(List<FPakInputPair> FilesToAdd)
//    {
//        String Root = GetLongestPath(FilesToAdd);
//        for (int FileIndex = 0; FileIndex < FilesToAdd.size() && BOOL(Root.length()); FileIndex++)
//        {
//            String Filename = FilesToAdd.get(FileIndex).Dest;
//            String Path = FPaths.GetPath(Filename) + TEXT("/");
//            int CommonSeparatorIndex = -1;
//            int SeparatorIndex = Path.indexOf('/');
//            while (SeparatorIndex >= 0)
//            {
//                if (FString.Strnicmp(Root, +0, Path, +0, SeparatorIndex + 1) != 0)
//                {
//                    break;
//                }
//                CommonSeparatorIndex = SeparatorIndex;
//                if (CommonSeparatorIndex + 1 < Path.length())
//                {
//                    SeparatorIndex = Path.indexOf('/', CommonSeparatorIndex + 1);
//                }
//                else
//                {
//                    break;
//                }
//            }
//            if ((CommonSeparatorIndex + 1) < Root.length())
//            {
//                Root = Root.substring(0, CommonSeparatorIndex + 1);
//            }
//        }
//        return Root;
//    }


}
