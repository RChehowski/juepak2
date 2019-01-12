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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
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
import java.util.function.LongConsumer;
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

                    final FPakEntry entry = copyCompressToPak(fis, fileLength, channel, setup.encryptContent, 64 * 1024);
                    final Path relativized = commonPath.relativize(path);

                    hashMap.put(PathUtils.pathToPortableUE4String(relativized), entry);
                }
            }

            final long indexOffset = channel.size();

            // !!! Write index
            // Write prefix
            final ByteBuffer entriesBuffer = ByteBuffer.allocate(128 * 1024);

            UE4Serializer.Write(entriesBuffer, "../../../");
            UE4Serializer.Write(entriesBuffer, hashMap.size());

            // Write entries
            hashMap.forEach((k, v) -> {
                UE4Serializer.Write(entriesBuffer, k);
                v.Serialize(entriesBuffer, setup.pakVersion);
            });

            // Determine index size
            final long indexSize = (channel.size() - indexOffset) + entriesBuffer.position();

            // Write info
            final FPakInfo pakInfo = new FPakInfo();

            pakInfo.Magic = FPakInfo.PakFile_Magic;
            pakInfo.Version = setup.pakVersion;
            pakInfo.IndexOffset = indexOffset;
            pakInfo.IndexSize = indexSize;
            pakInfo.bEncryptedIndex = Misc.toByte(setup.encryptIndex);
            FSHA1.HashBuffer(entriesBuffer.array(), entriesBuffer.position(), pakInfo.IndexHash);

            pakInfo.Serialize(entriesBuffer);

            entriesBuffer.flip();
            channel.write(entriesBuffer);
        }
    }

    private static List<File> deflateFileNEW(InputStream is, boolean bEncrypt, int MaxCompressionBlockSize,
                                             byte[] OutHashBytes, LongConsumer numBytesConsumer)
            throws IOException
    {
        final int READ_BUFFER_LENGTH = 512;
        final int WRITE_SIZE_DELTA = MaxCompressionBlockSize - READ_BUFFER_LENGTH;

        final byte[] readBuffer = new byte[READ_BUFFER_LENGTH];
        final byte[] blockBuffer = new byte[MaxCompressionBlockSize];

        final List<File> tempFiles = new ArrayList<>();
        final Deflater deflater = new Deflater();

        // Nullify hash bytes
        Arrays.fill(OutHashBytes, (byte)0);

        // Reset sha1 instance
        Sha1.reset();

        try {
//            long bytesReadTotal = 0;
//            long bytesWrittenTotal = 0;

            boolean readIsDone = false;
            while (!readIsDone)
            {
                int bytesWrittenPerBlock = 0;
                int bytesReadPerBlock = 0;

                int bytesReadPerTransmission;
                while ((bytesWrittenPerBlock < WRITE_SIZE_DELTA) && ((bytesReadPerTransmission = is.read(readBuffer)) > 0))
                {
                    // Update hash with raw, not deflated, not encrypted file data
                    Sha1.update(readBuffer, 0, bytesReadPerTransmission);

                    // Invoke consumer if some
                    if (numBytesConsumer != null)
                        numBytesConsumer.accept(bytesReadPerTransmission);

                    // Deflate until done
                    if (!deflater.finished())
                    {
                        deflater.setInput(readBuffer, 0, bytesReadPerTransmission);
                        while (!deflater.needsInput())
                        {
                            final int len = blockBuffer.length - bytesWrittenPerBlock;
                            final int bytesDeflated = deflater.deflate(blockBuffer, bytesWrittenPerBlock, len, Deflater.SYNC_FLUSH);

                            if (bytesDeflated > 0)
                                bytesWrittenPerBlock += bytesDeflated;
                        }
                    }

                    bytesReadPerBlock += bytesReadPerTransmission;
                }

//                bytesReadTotal += bytesReadPerBlock;
//                bytesWrittenTotal += bytesWrittenPerBlock;

                if (bytesReadPerBlock > 0)
                {
                    // Create and immediately put into the set of files
                    final File tempFile = File.createTempFile("juepak_temp_", ".cblock");
                    tempFiles.add(tempFile);

                    final int sizeToWrite = bEncrypt ? Align(bytesWrittenPerBlock, FAES.getBlockSize()) : bytesReadPerBlock;

                    if (sizeToWrite > MaxCompressionBlockSize)
                        throw new IllegalStateException("Too huge block: " + sizeToWrite + " bytes, allowed: " + MaxCompressionBlockSize);

                    // Maybe user desired to encrypt data?
                    if (bEncrypt)
                    {
                        FAES.EncryptData(blockBuffer, sizeToWrite, SharedKeyBytes);
                    }

                    // Finally, write the data
                    try (final OutputStream fis = new FileOutputStream(tempFile))
                    {
                        fis.write(blockBuffer, 0, sizeToWrite);
                    }
                }
                else
                {
                    readIsDone = true;
                }
            }

            // Compute final SHA1
            Sha1.digest(OutHashBytes);


//            final float l = ((bytesReadTotal - bytesWrittenTotal) / (float)bytesReadTotal) * 100.0f;
//
//            System.out.println(String.join(System.lineSeparator(),
//                "Compressing done.",
//                    "Bytes read    : " + bytesReadTotal,
//                    "Bytes written : " + bytesWrittenTotal,
//                    "Size " + ((l < 0) ? "in" : "de") + "creased: " + Math.abs((int)l) + "%"
//            ));
        }
        catch (RuntimeException | IOException e)
        {
            for (File tempFile : tempFiles)
                tempFile.delete();

            tempFiles.clear();

            // Rethrow
            throw e;
        }

        return tempFiles;
    }

    private synchronized FPakEntry copyCompressToPak(
            InputStream is, final long fileLength,
            SeekableByteChannel os, boolean bEncrypt,
            int MaxCompressionBlockSize) throws IOException
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



    // ! Static builder !
    public static PackerSetup builder()
    {
        return new PackerSetup();
    }
}
