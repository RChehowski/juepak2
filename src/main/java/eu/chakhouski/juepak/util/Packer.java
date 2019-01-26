package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.pak.FPakCompressedBlock;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.ECompressionFlags;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FMath;
import eu.chakhouski.juepak.ue4.FSHA1;
import eu.chakhouski.juepak.ue4.PakVersion;

import java.io.*;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import java.util.zip.Deflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class Packer implements Closeable
{
    /**
     * Max compressed buffer size according to UE4 spec
     */
    private static final int MAX_COMPRESSED_BUFFER_SIZE = 64 * 1024;

    /**
     * sha1 digest instance
     */
    private static final MessageDigest sha1;

    static
    {
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Small raw data buffer.
     */
    private final byte[] sharedReadBuffer = new byte[MAX_COMPRESSED_BUFFER_SIZE];
    /**
     * A larger deflated data buffer.
     */
    private final byte[] sharedWriteBuffer = new byte[MAX_COMPRESSED_BUFFER_SIZE];
    /**
     * Key bytes MUST BE NULLIFIED when decryption is done.
     * This should be done in the finally{} block to clear cached data even if the exception was thrown.
     */
    private final byte[] sharedKeyBytes = new byte[32];
    /**
     * Shared deflate state machine.
     */
    private final Deflater sharedDeflater = new Deflater();
    /**
     * List of all attached progress listeners.
     */
    private final List<DoubleConsumer> progressListeners = new ArrayList<>();
    /**
     * Packer setup, initialized once
     */
    private final PackerSetup setup;
    /**
     * Raw paths to add (both uniqueness and order are guaranteed)
     */
    private final Map<Path, PackParameters> paths = new LinkedHashMap<>();
    /**
     * A flag, determine whether the packer was closed.
     */
    private boolean closed = false;

    private long bytesTotal = 0;
    private long bytesProcessed = 0;

    /**
     * A private constructor, used by a builder.
     *
     * @param packerSetup A setup to be used as initial parameters/
     */
    private Packer(PackerSetup packerSetup)
    {
        this.setup = packerSetup;
    }

    // ! Static builder !
    public static PackerSetup builder()
    {
        return new PackerSetup();
    }

    /**
     * Add a path to a file to be packed with default parameters.
     *
     * @param path Path to a file to be packed into an archive.
     */
    @SuppressWarnings("unused")
    public void add(Path path)
    {
        add(path, PackParameters.sharedDefaultParameters());
    }

    /**
     * Add a path to be packed with custom parameters.
     *
     * @param path   Path to a file to be packed into an archive.
     * @param params Packing parameters.
     */
    public void add(Path path, PackParameters params)
    {
        ensureNotClosed();

        // Perform some runtime checks
        if ((setup.pakVersion < FPakInfo.PakFile_Version_DeleteRecords) && params.deleteRecord)
        {
            throw new IllegalArgumentException("Delete Record is not supported for the pak version: " +
                    FPakInfo.pakFileVersionToString(setup.pakVersion));
        }

        if ((setup.pakVersion < FPakInfo.PakFile_Version_CompressionEncryption) && (params.compress || params.encrypt))
        {
            throw new IllegalArgumentException("Neither compression nor encryption are not supported for the pak version: " +
                    FPakInfo.pakFileVersionToString(setup.pakVersion));
        }

        paths.put(path, params);
    }

    // ! Push into array !
    @Override
    public void close() throws IOException
    {
        ensureNotClosed();

        final Path archivePath = setup.archivePath;
        if (Files.isRegularFile(archivePath))
        {
            Files.delete(archivePath);
        }

        long bytesToBePacked = 0;
        for (Entry<Path, Packer.PackParameters> e : paths.entrySet())
        {
            bytesToBePacked += Files.size(e.getKey());
        }
        this.bytesTotal = bytesToBePacked;

        // Must keep user's order, so use linked map
        final Map<String, FPakEntry> nameEntryMap = new LinkedHashMap<>();

        // Write everything into archive file
        try (final FileOutputStream fos = new FileOutputStream(archivePath.toFile()))
        {
            final Path commonPath = PathUtils.findCommonPath(false, paths.keySet());
            final FileChannel c = fos.getChannel();

            for (final Entry<Path, PackParameters> e : paths.entrySet())
            {
                final Path path = Objects.requireNonNull(e.getKey());
                final PackParameters params = Objects.requireNonNull(e.getValue());

                try (final InputStream fis = new FileInputStream(path.toFile()))
                {
                    final FPakEntry entry;
                    if (params.compress)
                    {
                        entry = copyToPakCompressed(fis, c, params.encrypt);
                    }
                    else
                    {
                        entry = copyToPakUncompressed(fis, c, params.encrypt);
                    }

                    nameEntryMap.put(PathUtils.pathToPortableUE4String(commonPath.relativize(path)), entry);
                }
            }

            // Instantiate info (before index was written)
            final FPakInfo pakInfo = new FPakInfo();
            pakInfo.IndexOffset = c.position();
            pakInfo.Magic = FPakInfo.PakFile_Magic;
            pakInfo.Version = setup.pakVersion;
            pakInfo.bEncryptedIndex = Misc.toByte(setup.encryptIndex);

            // Calculate or just get a mount point
            final String mountPoint;
            if (setup.customMountPoint != null)
                mountPoint = setup.customMountPoint;
            else
                throw new NullPointerException("Non-manual mount points currently not implemented");

            // Write index
            final ByteBuffer entriesBuffer = serializeIndex(nameEntryMap, mountPoint, pakInfo.IndexHash);
            c.write(entriesBuffer);

            // Store index size (after index was written)
            pakInfo.IndexSize = entriesBuffer.limit();

            // Finally, serialize index, may allocate direct buffer because we never need an array
            final ByteBuffer infoBuffer = ByteBuffer.allocateDirect(toInt(pakInfo.GetSerializedSize(setup.pakVersion)));
            pakInfo.Serialize(infoBuffer);
            c.write((ByteBuffer) infoBuffer.flip());
        }
        finally
        {
            closed = true;
        }
    }

    private FPakEntry copyToPakUncompressed(InputStream is, SeekableByteChannel os, boolean encrypt) throws IOException
    {
        final long entryOffset = os.position();

        final FPakEntry entry = new FPakEntry();
        os.position(entryOffset + entry.GetSerializedSize(setup.pakVersion));

        sha1.reset();

        final ByteBuffer buffer = ByteBuffer.wrap(sharedWriteBuffer);

        long readTotal = 0;
        int readPerTransmission;

        if (encrypt)
        {
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(sharedKeyBytes);
        }

        while ((readPerTransmission = is.read(sharedWriteBuffer)) > 0)
        {
            final int bytesToWrite = encrypt ? Align(readPerTransmission, FAES.getBlockSize()) : readPerTransmission;

            if (encrypt)
            {
                // Add trailing zeroes if alignment has been applied
                if (readPerTransmission != bytesToWrite)
                {
                    Arrays.fill(sharedWriteBuffer, readPerTransmission, bytesToWrite, (byte) 0);
                }

                FAES.EncryptData(sharedWriteBuffer, bytesToWrite, sharedKeyBytes);
            }

            sha1.update(sharedWriteBuffer, 0, readPerTransmission);
            os.write((ByteBuffer) buffer.position(0).limit(bytesToWrite));

            // Increment counters
            readTotal += readPerTransmission;
            onBytesProcessed(readPerTransmission);
        }

        if (encrypt)
        {
            Arrays.fill(sharedKeyBytes, (byte) 0);
        }

        // Finish entry fulfilment
        entry.Offset = entryOffset;
        entry.Size = readTotal;
        entry.UncompressedSize = readTotal;
        entry.CompressionMethod = ECompressionFlags.COMPRESS_None;

        try {
            sha1.digest(entry.Hash, 0, sha1.getDigestLength());
        }
        catch (DigestException e) {
            throw new IOException(e);
        }

        entry.SetEncrypted(encrypt);
        entry.SetDeleteRecord(false); // No support yet

        // Serialize pak entry
        final ByteBuffer entryBuffer = ByteBuffer.allocateDirect(toInt(entry.GetSerializedSize(setup.pakVersion)));
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

        for (Entry<String, FPakEntry> entry : nameEntryMap.entrySet())
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
        for (Entry<String, FPakEntry> entry : nameEntryMap.entrySet())
        {
            UE4Serializer.Write(entriesBuffer, entry.getKey());
            entry.getValue().Serialize(entriesBuffer, setup.pakVersion);
        }

        // Flip and check whether the size is correct
        entriesBuffer.flip();

        if (entriesBuffer.limit() != serializeSize)
        {
            throw new IllegalStateException("Invalid serialize size, possible algorithm error");
        }

        // Compute hash
        FSHA1.HashBuffer(entriesBuffer.array(), bufferSize, outIndexHash);

        // Then encrypt if necessary
        if (setup.encryptIndex)
        {
            entriesBuffer.limit(bufferSize);

            try
            {
                FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(sharedKeyBytes);

                // Add trailing zeroes if alignment has been applied
                if (serializeSize != bufferSize)
                {
                    Arrays.fill(entriesBuffer.array(), serializeSize, bufferSize, (byte) 0);
                }

                FAES.EncryptData(entriesBuffer.array(), bufferSize, sharedKeyBytes);
            }
            finally
            {
                Arrays.fill(sharedKeyBytes, (byte) 0);
            }
        }

        return entriesBuffer;
    }

    private synchronized FPakEntry copyToPakCompressed(InputStream is, SeekableByteChannel os, boolean encrypt)
            throws IOException
    {
        final long beginPosition = os.position();

        // Remembered size and raw size
        final AtomicLong compressedSize = new AtomicLong();
        final AtomicLong uncompressedSize = new AtomicLong();

        final FPakEntry entry = new FPakEntry();

        // Split our data into compressed (and maybe encrypted) chunks
        final List<File> compressedTempBlocks = deflateSplit(is, uncompressedSize, compressedSize, entry.Hash, encrypt);

        // Setup an entry
        entry.Offset = beginPosition;
        entry.Size = compressedSize.longValue();
        entry.UncompressedSize = uncompressedSize.longValue();
        entry.CompressionMethod = ECompressionFlags.COMPRESS_ZLIB;
        entry.CompressionBlocks = new FPakCompressedBlock[compressedTempBlocks.size()];
        entry.CompressionBlockSize = MAX_COMPRESSED_BUFFER_SIZE;

        entry.SetDeleteRecord(false); // No support yet
        entry.SetEncrypted(encrypt);

        // Pak entry size is already known since we've initialized
        final long pakEntrySize = entry.GetSerializedSize(setup.pakVersion);
        final long baseOffset = (setup.pakVersion >= FPakInfo.PakFile_Version_RelativeChunkOffsets) ? 0 : beginPosition;

        long relativeChunkOffset = pakEntrySize;
        for (int i = 0; i < entry.CompressionBlocks.length; i++)
        {
            final File tempFile = compressedTempBlocks.get(i);

            final long chunkStart = baseOffset + relativeChunkOffset;
            final long chunkLength = tempFile.length();

            // Store in array
            entry.CompressionBlocks[i] = new FPakCompressedBlock(chunkStart, chunkStart + chunkLength);

            // Advance a relative offset
            relativeChunkOffset += chunkLength;
        }

        // 1. WRITE a pak entry (header)
        final ByteBuffer entryBuffer = ByteBuffer.allocateDirect(toInt(pakEntrySize)).order(ByteOrder.LITTLE_ENDIAN);
        entry.Serialize(entryBuffer, setup.pakVersion);

        entryBuffer.flip();
        os.write(entryBuffer);

        // 2. WRITE data connecting chunks
        final ByteBuffer blockBuffer = ByteBuffer.wrap(sharedWriteBuffer).order(ByteOrder.LITTLE_ENDIAN);

        for (final File blockFile : compressedTempBlocks)
        {
            try (final FileInputStream tis = new FileInputStream(blockFile))
            {
                int numBytesRead;
                while ((numBytesRead = tis.read(blockBuffer.array())) > 0)
                {
                    os.write((ByteBuffer) blockBuffer.position(0).limit(numBytesRead));
                }
            }

            // Delete temporary file and weakly check
            if (!blockFile.delete())
            {
                System.err.println("Warning: unable to delete: " + blockFile.toString());
            }
        }
        compressedTempBlocks.clear();

        return entry;
    }

    private List<File> deflateSplit(InputStream is, final AtomicLong outUncompressedSize, final AtomicLong outCompressedSize,
                                    byte[] outHash, boolean encrypt) throws IOException
    {
        Objects.requireNonNull(outUncompressedSize);
        Objects.requireNonNull(outCompressedSize);
        Objects.requireNonNull(outHash);

        if (encrypt)
        {
            if (FCoreDelegates.GetPakEncryptionKeyDelegate().IsBound())
            {
                FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(sharedKeyBytes);
            }
            else
            {
                throw new IllegalStateException("Unable to encrypt data, Encryption Delegate is not bound");
            }
        }

        final List<File> tempFiles = new ArrayList<>();

        final byte[] readBuffer = this.sharedWriteBuffer;
        final byte[] writeBuffer = this.sharedReadBuffer;

        // Nullify hash bytes
        Arrays.fill(outHash, (byte) 0);
        sha1.reset();

        boolean caughtAnException = false;
        try
        {
            int bytesReadPerTransmission;
            while ((bytesReadPerTransmission = is.read(readBuffer, 0, MAX_COMPRESSED_BUFFER_SIZE)) > 0)
            {
                // Prepare deflate
                sharedDeflater.reset();
                sharedDeflater.setInput(readBuffer, 0, bytesReadPerTransmission);
                sharedDeflater.finish();

                // Create and immediately put into the list of files
                final File tempFile = File.createTempFile("juepak_temp_", ".cblock");
                tempFiles.add(tempFile);

                // Deflate and write the data
                try (final OutputStream fis = new FileOutputStream(tempFile))
                {
                    while (!sharedDeflater.finished())
                    {
                        final int bytesDeflated = sharedDeflater.deflate(writeBuffer);
                        final int bytesToWrite;
                        if (encrypt)
                        {
                            bytesToWrite = Align(bytesDeflated, FAES.getBlockSize());

                            // Add trailing zeroes if alignment has been applied
                            if (bytesDeflated != bytesToWrite)
                            {
                                Arrays.fill(writeBuffer, bytesDeflated, bytesToWrite, (byte) 0);
                            }

                            FAES.EncryptData(writeBuffer, bytesToWrite, sharedKeyBytes);
                        }
                        else
                        {
                            bytesToWrite = bytesDeflated;
                        }

                        // Compute hash
                        sha1.update(writeBuffer, 0, bytesToWrite);
                        fis.write(writeBuffer, 0, bytesToWrite);

                        outCompressedSize.getAndAdd(bytesToWrite);
                    }
                }

                outUncompressedSize.getAndAdd(bytesReadPerTransmission);
                onBytesProcessed(bytesReadPerTransmission);
            }

            // Compute final SHA1
            if (sha1.digest(outHash, 0, outHash.length) != outHash.length)
            {
                throw new IOException("Invalid sha1 length: must be exactly " + outHash.length + " bytes");
            }
        }
        catch (IOException | RuntimeException e)
        {
            caughtAnException = true;
            throw e;
        }
        catch (DigestException e)
        {
            // Method has do DigestException in it's signature, so wrap into the RuntimeException
            caughtAnException = true;
            throw new IOException(e);
        }
        finally
        {
            // Nullify all intermediate buffers for security reasons
            Arrays.fill(readBuffer, (byte) 0);
            Arrays.fill(writeBuffer, (byte) 0);

            // Remove temporary files if there were any exceptions
            if (caughtAnException)
            {
                Misc.deleteFiles(tempFiles);
                tempFiles.clear();
            }

            // Nullify key bytes for security reasons as well
            if (encrypt)
            {
                Arrays.fill(sharedKeyBytes, (byte) 0);
            }
        }

        return tempFiles;
    }

    private void ensureNotClosed()
    {
        if (closed)
        {
            throw new UnsupportedOperationException("This packer is closed, create a new one");
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    private void onBytesProcessed(int numBytesProcessed)
    {
        bytesProcessed += numBytesProcessed;
        final double clampedProgress = FMath.Clamp((double) bytesProcessed / bytesTotal, 0.0, 1.0);

        for (int i = 0, progressListenersSize = progressListeners.size(); i < progressListenersSize; i++)
        {
            final DoubleConsumer progressListener = progressListeners.get(i);
            if (progressListener != null)
            {
                progressListener.accept(clampedProgress);
            }
        }
    }

    public void addProgressListener(DoubleConsumer progressListener)
    {
        progressListeners.add(progressListener);
    }

    public static final class PackParameters
    {
        private static PackParameters defaultParameters;

        private boolean encrypt;
        private boolean deleteRecord;
        private boolean compress;

        @SuppressWarnings("WeakerAccess")
        public static PackParameters sharedDefaultParameters()
        {
            if (defaultParameters == null)
            {
                defaultParameters = new PackParameters();
            }

            return defaultParameters;
        }

        public PackParameters encrypt()
        {
            encrypt = true;
            return this;
        }

        public PackParameters deleteRecord()
        {
            deleteRecord = true;
            return this;
        }

        public PackParameters compress()
        {
            compress = true;
            return this;
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

        public PackerSetup encryptIndex(boolean value)
        {
            encryptIndex = value;
            return this;
        }


        public PackerSetup pakVersion(int value)
        {
            pakVersion = value;
            return this;
        }

        public PackerSetup engineVersion(String value)
        {
            pakVersion = PakVersion.getByEngineVersion(value);
            return this;
        }

        public PackerSetup customMountPoint(String value)
        {
            customMountPoint = value;
            return this;
        }

        public PackerSetup archiveFile(Path value)
        {
            archivePath = value;
            return this;
        }

        public Packer build()
        {
            // Check whether the user requested index encryption but the feature is not supported.
            if ((pakVersion < FPakInfo.PakFile_Version_IndexEncryption) && encryptIndex)
            {
                throw new IllegalStateException("Unable to encrypt index, feature is not supported. Pak version is " +
                        FPakInfo.pakFileVersionToString(pakVersion));
            }

            return new Packer(this);
        }
    }
}
