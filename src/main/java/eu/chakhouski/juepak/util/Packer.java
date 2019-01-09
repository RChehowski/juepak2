package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.ECompressionFlags;
import eu.chakhouski.juepak.pak.FPakCompressedBlock;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FSHA1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.ue4.AlignmentTemplates.AlignDown;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class Packer
{
    public static final class PackerSetup
    {
        private boolean encryptIndex = false;
        private boolean encryptContent = false;
        private boolean compressContent = false;

        private int pakVersion = FPakInfo.PakFile_Version_Latest;

        public PackerSetup encryptIndex(boolean value) {
            encryptIndex = value;
            return this;
        }

        public PackerSetup encryptContent(boolean value) {
            encryptContent = value;
            return this;
        }

        public PackerSetup compressContent(boolean value) {
            compressContent = value;
            return this;
        }


        public PackerSetup pakVersion(int value) {
            pakVersion = value;
            return this;
        }


        public Packer build() {
            return new Packer(this);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Key bytes must be nullified when decryption is done.
     */
    private static final byte[] SharedKeyBytes = new byte[32];

    /**
     * Sha1 digest instance
     */
    private static final MessageDigest Sha1;

    static {
        try {
            Sha1 = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Packer setup
    private final PackerSetup setup;

    // Raw paths to add (uniqueness guaranteed)
    private final Set<Path> paths = new HashSet<>();



    private Packer(PackerSetup packerSetup)
    {
        this.setup = packerSetup;
    }



    // ! Add and bulk add !
    public void add(Path path)
    {
        paths.add(path);
    }

    public void add(Path... paths)
    {
        add(Arrays.asList(paths));
    }

    public void add(Collection<Path> paths)
    {
        this.paths.addAll(paths);
    }


    // ! Push into array !
    public void closeAndWrite(Path savePath) throws IOException
    {
        // !!! Computed data
        final Path commonPath = PathUtils.findCommonPath(false, paths);

        final Map<String, FPakEntry> hashMap = new HashMap<>(paths.size());

        try (final FileOutputStream fos = new FileOutputStream(savePath.toFile()))
        {
            final FileChannel channel = fos.getChannel();

            for (final Path path : paths)
            {
                final File file = path.toFile();
                try (final FileInputStream fis = new FileInputStream(file))
                {
                    final long fileLength = file.length();

                    final FPakEntry entry = deflateFile(fis, fileLength, channel, setup.encryptContent, 64 * 1024);
                    final Path relativized = commonPath.relativize(path);

                    hashMap.put(PathUtils.pathToPortableUE4String(relativized), entry);
                }
            }

            final long indexOffset = channel.size();
            System.out.println("indexOffset " + indexOffset);

            // !!! Write index
            // Write prefix
            final ByteBuffer entriesBuffer = ByteBuffer.allocate(128 * 1024);

            UE4Serializer.WriteString(entriesBuffer, "../../../");
            UE4Serializer.WriteInt(entriesBuffer, hashMap.size());

            // Write entries
            hashMap.forEach((k, v) -> {
                UE4Serializer.WriteString(entriesBuffer, k.toString());
                v.Serialize(entriesBuffer, setup.pakVersion);
            });

            // Determine index size
            final long indexSize = (channel.size() - indexOffset) + entriesBuffer.position();
            System.out.println("indexSize " + indexSize);

            // Write info
            final FPakInfo pakInfo = new FPakInfo();

            pakInfo.Magic = FPakInfo.PakFile_Magic;
            pakInfo.Version = setup.pakVersion;
            pakInfo.IndexOffset = indexOffset;
            pakInfo.IndexSize = indexSize;
            pakInfo.IndexHash = new byte[20];
            pakInfo.bEncryptedIndex = Misc.toByte(setup.encryptIndex);

            System.out.println("Buf: " + entriesBuffer.toString());
            FSHA1.HashBuffer(entriesBuffer.array(), entriesBuffer.position(), pakInfo.IndexHash);

            pakInfo.Serialize(entriesBuffer);

            entriesBuffer.flip();
            channel.write(entriesBuffer);

            System.out.println("CachedTotalSize " + channel.size());
        }
    }

    private FPakEntry deflateFile(InputStream is, final long fileLength, FileChannel os, boolean bEncrypt, int MaxCompressionBlockSize)
            throws IOException
    {
        final long numBlocks = (long)Math.ceil((double) fileLength / MaxCompressionBlockSize);

        // Store initial position to write here a FPakEntry instance a bit later
        final long entryOffset = os.position();

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


        final FPakEntry entry = new FPakEntry();
        entry.CompressionBlocks = new FPakCompressedBlock[toInt(numBlocks)];
        entry.CompressionMethod = ECompressionFlags.COMPRESS_ZLIB;
        entry.SetEncrypted(bEncrypt);
        entry.SetDeleteRecord(false);

        final long entrySerializedSize = entry.GetSerializedSize(setup.pakVersion);
        os.position(os.position() + entrySerializedSize);

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
        entry.Offset = entryOffset;
        entry.UncompressedSize = uncompressedSize;
        entry.Size = size;
        entry.Hash = Sha1.digest();
        blocks.toArray(entry.CompressionBlocks);
        entry.CompressionBlockSize = compressionBlockSize;

        // Calculate relative offset
        assert entrySerializedSize == entry.GetSerializedSize(setup.pakVersion);
        final long offset = (setup.pakVersion < FPakInfo.PakFile_Version_RelativeChunkOffsets) ?
            entrySerializedSize : entryOffset + entrySerializedSize;


        for (final FPakCompressedBlock block : entry.CompressionBlocks)
            block.relativize(offset - entrySerializedSize);

        // Byte buffer
        final ByteBuffer entryBuffer = ByteBuffer.allocate(toInt(entrySerializedSize)).order(ByteOrder.LITTLE_ENDIAN);
        entry.Serialize(entryBuffer, setup.pakVersion);

        // Write entry
        os.position(entryOffset).write((ByteBuffer)entryBuffer.position(0));

        // Set position to an end
        os.position(os.size());

        return entry;
    }








    // lazy
    private Deflater deflater;

    private ByteBuffer deflateSrcBuffer;
    private ByteBuffer deflateDstBuffer;


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

    private ByteBuffer getDeflateDstBuffer(int n)
    {
        if (deflateDstBuffer == null)
        {
            assert n % FAES.AESBlockSize == 0;

            deflateDstBuffer = ByteBuffer.allocate(n);
        }

        return deflateDstBuffer;
    }



    // ! Static builder !
    public static PackerSetup builder()
    {
        return new PackerSetup();
    }
}
