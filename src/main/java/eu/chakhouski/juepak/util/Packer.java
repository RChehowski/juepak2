package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.pak.FPakCompressedBlock;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.ECompressionFlags;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FSHA1;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.zip.Deflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class Packer implements Closeable
{
    public static final class PackParameters
    {
        private static PackParameters defaultParameters;

        private boolean encrypt;
        private boolean deleteRecord;
        private boolean compress;

        public static PackParameters getDefaultParameters()
        {
            if (defaultParameters == null)
            {
                defaultParameters = new PackParameters();
            }

            return defaultParameters;
        }
    }

    /**
     * A builder used to construct a packer.
     */
    public static final class PackerSetup
    {
        private boolean encryptIndex = false;

        private int pakVersion = FPakInfo.PakFile_Version_Latest;
        private String customMountPoint = null;
        private Path archivePath = null;

        private PackerSetup()
        {
        }

        public PackerSetup encryptIndex(boolean value) {
            encryptIndex = value;
            return this;
        }


        public PackerSetup pakVersion(int value) {
            pakVersion = value;
            return this;
        }

        public PackerSetup customMountPoint(String value) {
            customMountPoint = value;
            return this;
        }

        public PackerSetup archiveFile(Path value) {
            archivePath = value;
            return this;
        }



        public Packer build() {
            return new Packer(this);
        }
    }

    /**
     * Max compressed buffer size according to UE4 spec
     */
    private static final int MAX_COMPRESSED_BUFFER_SIZE = 64 * 1024;

    private static final int DEFLATE_READ_BUFFER_LENGTH = 512;
    private static final int DEFLATE_MAX_FOOTER_LENGTH = 16;

    private static final int WRITE_SIZE_DELTA = MAX_COMPRESSED_BUFFER_SIZE -
        DEFLATE_READ_BUFFER_LENGTH - DEFLATE_MAX_FOOTER_LENGTH;


    /**
     * A flag, determine whether the packer was closed.
     */
    private boolean closed = false;

    /**
     * Small raw data buffer.
     */
    private final byte[] SharedDeflateReadBuffer = new byte[DEFLATE_READ_BUFFER_LENGTH];

    /**
     * A larger deflated data buffer.
     */
    private final byte[] SharedDeflateBlockBuffer = new byte[MAX_COMPRESSED_BUFFER_SIZE];

    /**
     * Key bytes MUST BE NULLIFIED when decryption is done.
     *
     * This should be done in the finally{} block to clear cached data even if the exception was thrown.
     */
    private final byte[] SharedKeyBytes = new byte[32];

    /**
     * Shared deflate state machine
     */
    private final Deflater deflater = new Deflater();


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

    // Packer setup, initialized once
    private final PackerSetup setup;

    // Raw paths to add (both uniqueness and order are guaranteed)
    private final Map<Path, PackParameters> paths = new LinkedHashMap<>();


    /**
     * A private constructor, used by a builder.
     *
     * @param packerSetup A setup to be used as initial parameters/
     */
    private Packer(PackerSetup packerSetup)
    {
        this.setup = packerSetup;
    }

    /**
     * Add a path to a file to be packed with default parameters.
     *
     * @param path Path to a file to be packed into an archive.
     */
    public void add(Path path)
    {
        add(path, PackParameters.getDefaultParameters());
    }

    /**
     * Add a path to be packed with custom parameters.
     *
     * @param path Path to a file to be packed into an archive.
     * @param params Packing parameters.
     */
    public void add(Path path, PackParameters params)
    {
        checkNotClosed();

        paths.put(path, params);
    }

    // ! Push into array !
    @Override
    public void close() throws IOException
    {
        checkNotClosed();

        final Path archivePath = setup.archivePath;

        // Must keep user's order, so use linked map
        final Map<String, FPakEntry> nameEntryMap = new LinkedHashMap<>();

        if (Files.isRegularFile(archivePath))
        {
            Files.delete(archivePath);
        }

        // Write everything into archive file
        try (final FileOutputStream fos = new FileOutputStream(archivePath.toFile()))
        {
            final FileChannel c = fos.getChannel();
            final Path commonPath = PathUtils.findCommonPath(false, paths.keySet());

            for (Map.Entry<Path, PackParameters> e : paths.entrySet())
            {
                final Path path = e.getKey();
                final PackParameters params = e.getValue();

                final File file = path.toFile();

                try (final InputStream fis = new FileInputStream(file))
                {
                    final FPakEntry entry;
                    if (params.compress)
                    {
                        entry = copyCompressToPak(fis, c, params.encrypt);
                    }
                    else
                    {
                        entry = copyToPak(fis, c, params.encrypt);
                    }

                    nameEntryMap.put(PathUtils.pathToPortableUE4String(commonPath.relativize(path)), entry);
                }
            }

            // Instantiate info (before index was written)
            final FPakInfo info = new FPakInfo();
            info.IndexOffset = c.position();
            info.Magic = FPakInfo.PakFile_Magic;
            info.Version = setup.pakVersion;
            info.bEncryptedIndex = Misc.toByte(setup.encryptIndex);

            // Calculate or just get a mount point
            final String mountPoint;
            if (setup.customMountPoint == null)
                throw new UnsupportedOperationException("Non-manual mount points currently not implemented");
            else
                mountPoint = setup.customMountPoint;

            // Write index
            final ByteBuffer entriesBuffer = serializeIndex(nameEntryMap, mountPoint, info.IndexHash);
            c.write(entriesBuffer);

            // Store index size (after index was written)
            info.IndexSize = entriesBuffer.limit();

            // Finally, serialize index, may allocate direct buffer because we never need an array
            final ByteBuffer infoBuffer = ByteBuffer.allocateDirect(toInt(info.GetSerializedSize(setup.pakVersion)));
            info.Serialize(infoBuffer);
            c.write((ByteBuffer) infoBuffer.flip());
        }
        finally
        {
            closed = true;
        }
    }

    private FPakEntry copyToPak(InputStream is, SeekableByteChannel os, boolean encrypt) throws IOException
    {
        final long entryOffset = os.position();

        Sha1.reset();


        final FPakEntry entry = new FPakEntry();

        // Size is constant
        os.position(entryOffset + entry.GetSerializedSize(setup.pakVersion));


        long bytesReadTotal = 0;
        int bytesReadPerTransmission;

        final ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        while ((bytesReadPerTransmission = is.read(buffer.array())) > 0)
        {
            buffer.position(0).limit(bytesReadPerTransmission);
            os.write(buffer);

            Sha1.update(buffer.array(), 0, bytesReadPerTransmission);

            bytesReadTotal += bytesReadPerTransmission;
        }

        // Finish entry fulfilment
        entry.Offset = entryOffset;
        entry.Size = bytesReadTotal;
        entry.UncompressedSize = bytesReadTotal;
        entry.CompressionMethod = ECompressionFlags.COMPRESS_None;

        try {
            Sha1.digest(entry.Hash, 0, Sha1.getDigestLength());
        }
        catch (DigestException e) {
            throw new RuntimeException(e);
        }

        entry.SetEncrypted(encrypt);
        entry.SetDeleteRecord(false); // No support yet

        // Serialize pak entry
        final ByteBuffer entryBuffer = ByteBuffer.allocate(toInt(entry.GetSerializedSize(setup.pakVersion)));
        entry.Serialize(entryBuffer, setup.pakVersion);
        entryBuffer.flip();

        // Finally, write and restore a position
        os.position(entryOffset).write(entryBuffer);
        os.position(os.size());

        return entry;
    }

    private ByteBuffer serializeIndex(Map<String, FPakEntry> nameEntryMap, String mountPoint, byte[] outIndexHash)
    {
        // 1.
        // First pass - compute buffer size
        int serializeSize = 0;

        serializeSize += UE4Serializer.GetSerializeSize(mountPoint);
        serializeSize += sizeof(nameEntryMap.size());

        for (Map.Entry<String, FPakEntry> entry : nameEntryMap.entrySet())
        {
            serializeSize += UE4Serializer.GetSerializeSize(entry.getKey());
            serializeSize += entry.getValue().GetSerializedSize(setup.pakVersion);
        }

        // Compute buffer size (possible with serialization padding)
        final int bufferSize = (setup.encryptIndex) ? Align(serializeSize, FAES.getBlockSize()) : serializeSize;

        // 2.
        // Second pass - actually serialize
        final ByteBuffer entriesBuffer = ByteBuffer.allocate(bufferSize);

        UE4Serializer.Write(entriesBuffer, mountPoint);
        UE4Serializer.Write(entriesBuffer, nameEntryMap.size());

        // Write entries
        for (Map.Entry<String, FPakEntry> entry : nameEntryMap.entrySet())
        {
            UE4Serializer.Write(entriesBuffer, entry.getKey());
            entry.getValue().Serialize(entriesBuffer, setup.pakVersion);
        }

        // Flip and check whether the size is correct
        entriesBuffer.flip();

        if (entriesBuffer.limit() != serializeSize)
            throw new IllegalStateException("Invalid serialize size, possible algorithm error");

        // Compute hash
        FSHA1.HashBuffer(entriesBuffer.array(), bufferSize, outIndexHash);

        // Then encrypt if necessary
        if (setup.encryptIndex)
        {
            entriesBuffer.limit(bufferSize);

            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);
            FAES.EncryptData(entriesBuffer.array(), entriesBuffer.limit(), SharedKeyBytes);
            Arrays.fill(SharedKeyBytes, (byte)0);
        }

        return entriesBuffer;
    }

    private List<File> deflateFile(InputStream is, final AtomicLong outUncompressedSize, final AtomicLong outCompressedSize,
                                   byte[] outHash, boolean encrypt, LongConsumer bytesCounter) throws IOException
    {
        Objects.requireNonNull(outUncompressedSize);
        Objects.requireNonNull(outCompressedSize);
        Objects.requireNonNull(outHash);
        Objects.requireNonNull(bytesCounter);


        boolean caughtAnyException = false;

        final List<File> tempFiles = new ArrayList<>();

        final byte[] readBuffer = SharedDeflateReadBuffer;
        final byte[] blockBuffer = SharedDeflateBlockBuffer;

        // Nullify hash bytes and reset sha1 instance
        Arrays.fill(outHash, (byte) 0);
        Sha1.reset();

        try
        {
            boolean readingIsDone = false;
            while (!readingIsDone)
            {
                int bytesWrittenPerBlock = 0;
                int bytesReadPerBlock = 0;

                deflater.reset();

                int bytesReadPerTransmission;
                while ((bytesWrittenPerBlock < WRITE_SIZE_DELTA) && ((bytesReadPerTransmission = is.read(readBuffer)) > 0))
                {
                    // Update hash with raw, not deflated, not encrypted file data
                    Sha1.update(readBuffer, 0, bytesReadPerTransmission);

                    // Invoke consumer if some
                    bytesCounter.accept(bytesReadPerTransmission);

                    // Deflate until done
                    deflater.setInput(readBuffer, 0, bytesReadPerTransmission);
                    while (!deflater.needsInput())
                    {
                        final int bytesRemaining = blockBuffer.length - bytesWrittenPerBlock;
                        final int bytesDeflated = deflater.deflate(blockBuffer, bytesWrittenPerBlock,
                                bytesRemaining, Deflater.SYNC_FLUSH);

                        if (bytesDeflated > 0)
                        {
                            bytesWrittenPerBlock += bytesDeflated;
                        }
                    }

                    bytesReadPerBlock += bytesReadPerTransmission;
                }

                if (bytesReadPerBlock > 0)
                {
                    final int bytesRemaining = blockBuffer.length - bytesWrittenPerBlock;

                    // Finish deflation
                    deflater.finish();
                    bytesWrittenPerBlock += deflater.deflate(blockBuffer, bytesWrittenPerBlock, bytesRemaining);

                    // Determine final chunk size to write
                    final int sizeToWrite = encrypt ? Align(bytesWrittenPerBlock, FAES.getBlockSize()) : bytesWrittenPerBlock;

                    if (sizeToWrite > MAX_COMPRESSED_BUFFER_SIZE)
                    {
                        throw new IllegalStateException("Too huge block: " + sizeToWrite + " bytes, allowed: " + MAX_COMPRESSED_BUFFER_SIZE);
                    }

                    // Append zeroes, instead of garbage to data space, lost due to AES block alignment.
                    if (bytesWrittenPerBlock != sizeToWrite)
                    {
                        Arrays.fill(blockBuffer, bytesReadPerBlock, sizeToWrite, (byte) 0);
                    }

                    if (encrypt)
                    {
                        FAES.EncryptData(blockBuffer, sizeToWrite, SharedKeyBytes);
                    }

                    // Create and immediately put into the list of files
                    final File tempFile = File.createTempFile("juepak_temp_", ".cblock");
                    tempFiles.add(tempFile);

                    // Finally, write the data
                    try (final FileOutputStream fis = new FileOutputStream(tempFile))
                    {
                        fis.write(blockBuffer, 0, sizeToWrite);
                    }
                }
                else
                {
                    readingIsDone = true;
                }

                outUncompressedSize.getAndAdd(bytesReadPerBlock);
                outCompressedSize.getAndAdd(bytesWrittenPerBlock);
            }

            // Compute final SHA1
            if (Sha1.digest(outHash, 0, outHash.length) != outHash.length)
            {
                throw new RuntimeException("Invalid hash length: must be exactly " + outHash.length + " bytes");
            }
        }
        catch (IOException | RuntimeException e)
        {
            caughtAnyException = true;
            throw e;
        }
        catch (DigestException e)
        {
            // Method has do DigestException in it's signature, so wrap into the RuntimeException
            caughtAnyException = true;
            throw new RuntimeException(e);
        }
        finally
        {
            // Nullify all intermediate buffers for security reasons
            Arrays.fill(readBuffer, (byte)0);
            Arrays.fill(blockBuffer, (byte)0);

            // Remove temporary files if there were any exceptions
            if (caughtAnyException)
            {
                boolean deleteFailures = false;
                for (File f : tempFiles)
                {
                    deleteFailures |= f.delete();
                }

                tempFiles.clear();

                // Display message if some failures (delete failures are non-fatal?)
                if (deleteFailures)
                {
                    System.err.println("Some errors occurred deleting temporary files");
                }
            }
        }

        return tempFiles;
    }

    private synchronized FPakEntry copyCompressToPak(InputStream is, SeekableByteChannel os,
                                                     boolean encrypt) throws IOException
    {
        final long beginPosition = os.position();
        final FPakEntry e = new FPakEntry();

        if (encrypt)
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);

        // Remembered size and raw size
        final AtomicLong uncompressedSize = new AtomicLong();
        final AtomicLong size = new AtomicLong();
        final AtomicLong bytesTotal = new AtomicLong();

        final List<File> compressedTempFiles = deflateFile(is, size, uncompressedSize, e.Hash, encrypt, bytesTotal::getAndAdd);

        if (encrypt)
            Arrays.fill(SharedKeyBytes, (byte)0);

        // Set entry
        e.Offset = beginPosition;
        e.Size = size.longValue();
        e.UncompressedSize = uncompressedSize.longValue();
        e.CompressionMethod = ECompressionFlags.COMPRESS_ZLIB;
        e.CompressionBlocks = new FPakCompressedBlock[compressedTempFiles.size()];
        e.CompressionBlockSize = MAX_COMPRESSED_BUFFER_SIZE;

        e.SetDeleteRecord(false); // No support yet
        e.SetEncrypted(encrypt);

        final long baseOffset = (setup.pakVersion >= FPakInfo.PakFile_Version_RelativeChunkOffsets) ? 0 : beginPosition;

        final long pakEntrySize = e.GetSerializedSize(setup.pakVersion);
        long relativeChunkOffset = pakEntrySize;

        for (int i = 0; i < e.CompressionBlocks.length; i++)
        {
            final File tempFile = compressedTempFiles.get(i);

            final long chunkStart = baseOffset + relativeChunkOffset;
            final long chunkLength = tempFile.length();

            // Store in array
            e.CompressionBlocks[i] = new FPakCompressedBlock(chunkStart, chunkStart + chunkLength);

            // Advance a relative offset
            relativeChunkOffset += chunkLength;
        }

        // Create and write the pak entry
        final ByteBuffer entryBuffer = ByteBuffer.allocate(toInt(pakEntrySize))
                .order(ByteOrder.LITTLE_ENDIAN);

        e.Serialize(entryBuffer, setup.pakVersion);
        os.write((ByteBuffer)entryBuffer.flip());

        // Write contents
        final ByteBuffer blockBuffer = ByteBuffer.allocate(MAX_COMPRESSED_BUFFER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        for (final Iterator<File> it = compressedTempFiles.iterator(); it.hasNext(); )
        {
            final File blockFile = it.next();
            try (final FileInputStream tis = new FileInputStream(blockFile))
            {
                final int numBytesRead = tis.read(blockBuffer.array());

                // Limit the buffer
                blockBuffer.position(0).limit(numBytesRead);
            }

            os.write(blockBuffer);

            // Delete temporary file and weakly check
            if (!blockFile.delete())
                System.err.println("Warning: unable to delete: " + blockFile.toString());

            // Remove from collection
            it.remove();
        }

        return e;
    }

    private void checkNotClosed()
    {
        if (closed)
            throw new UnsupportedOperationException("This packer is closed, create a new one");
    }



    // ! Static builder !
    public static PackerSetup builder()
    {
        return new PackerSetup();
    }
}
