package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.ECompressionFlags;
import eu.chakhouski.juepak.pak.FPakCompressedBlock;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FSHA1;
import org.apache.commons.lang.mutable.MutableLong;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.zip.Deflater;

import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.util.Misc.toInt;

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


    /**
     * Max compressed buffer size according to UE4 spec
     */
    private static final int MAX_COMPRESSED_BUFFER_SIZE = 64 * 1024;

    /**
     * Key bytes must be nullified when decryption is done.
     */
    private static final byte[] SharedKeyBytes = new byte[32];

    /**
     * Shared deflate state machine
     */
    private static final Deflater deflater = new Deflater();


    private static final int DEFLATE_READ_BUFFER_LENGTH = 512;
    private static final int DEFLATE_MAX_FOOTER_LENGTH = 16;

    private static final int WRITE_SIZE_DELTA = MAX_COMPRESSED_BUFFER_SIZE -
            DEFLATE_READ_BUFFER_LENGTH -
            DEFLATE_MAX_FOOTER_LENGTH;

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

    public void add(Path path)
    {
        paths.add(path);
    }


    // ! Push into array !
    public void closeAndWrite(Path savePath) throws IOException
    {
        // Must keep user's order, so use linked map
        final Map<String, FPakEntry> nameEntryMap = new LinkedHashMap<>();

        // Write everything into archive file
        try (final FileOutputStream fos = new FileOutputStream(savePath.toFile()))
        {
            final FileChannel c = fos.getChannel();
            final Path commonPath = PathUtils.findCommonPath(false, paths);

            for (final Path path : paths)
            {
                final File file = path.toFile();

                try (final InputStream fis = new FileInputStream(file))
                {
                    final FPakEntry entry = copyCompressToPak(fis, c, setup.encryptContent);
                    nameEntryMap.put(PathUtils.pathToPortableUE4String(commonPath.relativize(path)), entry);
                }
            }


            final byte[] indexHash = new byte[20];

            final long indexBegin = c.position();

            // Write index
            final ByteBuffer entriesBuffer = serializeIndex(nameEntryMap, "../../../", indexHash);

            c.write(entriesBuffer);




            // Write info
            final FPakInfo info = new FPakInfo(); {
                info.Magic = FPakInfo.PakFile_Magic;
                info.Version = setup.pakVersion;
                info.IndexOffset = indexBegin;
                info.IndexSize = entriesBuffer.limit();
                info.bEncryptedIndex = Misc.toByte(setup.encryptIndex);

                System.arraycopy(indexHash, 0, info.IndexHash, 0, indexHash.length);
            }

            final ByteBuffer infoBuffer = ByteBuffer.allocate(toInt(info.GetSerializedSize(setup.pakVersion)));
            info.Serialize(infoBuffer);

            c.write((ByteBuffer) infoBuffer.flip());
        }
    }

    private ByteBuffer serializeIndex(Map<String, FPakEntry> nameEntryMap, String mountPoint, byte[] outIndexHash)
    {
        // First pass - compute buffer size
        int serializeSize = 0;

        serializeSize += UE4Serializer.GetSerializeSize(mountPoint);
        serializeSize += UE4Serializer.GetSerializeSize(nameEntryMap.size());

        for (Map.Entry<String, FPakEntry> entry : nameEntryMap.entrySet())
        {
            serializeSize += UE4Serializer.GetSerializeSize(entry.getKey());
            serializeSize += entry.getValue().GetSerializedSize(setup.pakVersion);
        }

        // Compute buffer size (possible with serialization padding)
        final int bufferSize = (setup.encryptIndex) ? Align(serializeSize, FAES.getBlockSize()) : serializeSize;

        // Second pass - actually serialize
        final ByteBuffer entriesBuffer = ByteBuffer.allocate(bufferSize);

        UE4Serializer.Write(entriesBuffer, mountPoint);
        UE4Serializer.Write(entriesBuffer, nameEntryMap.size());

        // Write entries
        nameEntryMap.forEach((k, v) -> {
            UE4Serializer.Write(entriesBuffer, k);
            v.Serialize(entriesBuffer, setup.pakVersion);
        });

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


    private static List<File> deflateFile(InputStream is, boolean bEncrypt,
                                          final MutableLong uncompressedSize,
                                          final MutableLong size,
                                          byte[] OutHashBytes, LongConsumer bytesCounter)
            throws IOException
    {
        // Buffers
        final byte[] readBuffer = new byte[DEFLATE_READ_BUFFER_LENGTH];
        final byte[] blockBuffer = new byte[MAX_COMPRESSED_BUFFER_SIZE];

        final List<File> tempFiles = new ArrayList<>();

        // Nullify hash bytes and reset sha1 instance
        if (OutHashBytes != null)
        {
            Arrays.fill(OutHashBytes, (byte) 0);
            Sha1.reset();
        }

//        try {
            boolean readIsDone = false;
            while (!readIsDone)
            {
                int bytesWrittenPerBlock = 0;
                int bytesReadPerBlock = 0;

                deflater.reset();

                int bytesReadPerTransmission;
                while ((bytesWrittenPerBlock < WRITE_SIZE_DELTA) && ((bytesReadPerTransmission = is.read(readBuffer)) > 0))
                {
                    // Update hash with raw, not deflated, not encrypted file data
                    if (OutHashBytes != null)
                        Sha1.update(readBuffer, 0, bytesReadPerTransmission);

                    // Invoke consumer if some
                    if (bytesCounter != null)
                        bytesCounter.accept(bytesReadPerTransmission);

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

                if (bytesReadPerBlock > 0)
                {
                    // Finish deflation
                    deflater.finish();
                    bytesWrittenPerBlock += deflater.deflate(blockBuffer, bytesWrittenPerBlock, blockBuffer.length - bytesWrittenPerBlock);

                    // Determine final chunk size to write
                    final int sizeToWrite;
                    if (bEncrypt)
                        sizeToWrite = Align(bytesWrittenPerBlock, FAES.getBlockSize());
                    else
                        sizeToWrite = bytesWrittenPerBlock;

                    if (sizeToWrite > MAX_COMPRESSED_BUFFER_SIZE)
                        throw new IllegalStateException("Too huge block: " + sizeToWrite + " bytes, allowed: " + MAX_COMPRESSED_BUFFER_SIZE);

                    // Append zeroes, instead of garbage to data space, lost due to AES block alignment.
                    if (bytesWrittenPerBlock != sizeToWrite)
                        Arrays.fill(blockBuffer, bytesReadPerBlock, sizeToWrite, (byte)0);

                    // Maybe user desired to encrypt data?
                    if (bEncrypt)
                        FAES.EncryptData(blockBuffer, sizeToWrite, SharedKeyBytes);

                    // Create and immediately put into the set of files
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
                    readIsDone = true;
                }

                if (uncompressedSize != null)
                    uncompressedSize.add(bytesReadPerBlock);

                if (size != null)
                    size.add(bytesWrittenPerBlock);
            }

            // Compute final SHA1
            try {
                if (OutHashBytes != null)
                    Sha1.digest(OutHashBytes, 0, OutHashBytes.length);
            }
            catch (DigestException de) {
                throw new RuntimeException(de);
            }
//        }
//        catch (RuntimeException | IOException e)
//        {
//            for (File tempFile : tempFiles)
//                tempFile.delete();
//
//            tempFiles.clear();
//
//            // Rethrow
//            throw e;
//        }

        return tempFiles;
    }

    private synchronized FPakEntry copyCompressToPak(InputStream is, SeekableByteChannel os, boolean bEncrypt)
            throws IOException
    {
        final long beginPosition = os.position();
        final FPakEntry e = new FPakEntry();

        if (bEncrypt)
            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);

        // Remembered size and raw size
        final MutableLong uncompressedSize = new MutableLong();
        final MutableLong size = new MutableLong();

        final List<File> compressedTempFiles = deflateFile(is, bEncrypt, size, uncompressedSize, e.Hash, null);

        if (bEncrypt)
            Arrays.fill(SharedKeyBytes, (byte)0);

        // Set entry
        e.Offset = beginPosition;
        e.Size = size.longValue();
        e.UncompressedSize = uncompressedSize.longValue();
        e.CompressionMethod = ECompressionFlags.COMPRESS_ZLIB;
        e.CompressionBlocks = new FPakCompressedBlock[compressedTempFiles.size()];
        e.CompressionBlockSize = MAX_COMPRESSED_BUFFER_SIZE;

        e.SetDeleteRecord(false);
        e.SetEncrypted(bEncrypt);

        // Relativize compression blocks
        final FPakCompressedBlock[] compressionBlocks = e.CompressionBlocks;

        final long baseOffset = (setup.pakVersion >= FPakInfo.PakFile_Version_RelativeChunkOffsets) ? 0 : beginPosition;

        final long pakEntrySize = e.GetSerializedSize(setup.pakVersion);
        long relativeChunkOffset = pakEntrySize;

        for (int i = 0; i < compressionBlocks.length; i++)
        {
            final File tempFile = compressedTempFiles.get(i);

            final long chunkStart = baseOffset + relativeChunkOffset;
            final long chunkLength = tempFile.length();

            // Store in array
            compressionBlocks[i] = new FPakCompressedBlock(chunkStart, chunkStart + chunkLength);

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



    // ! Static builder !
    public static PackerSetup builder()
    {
        return new PackerSetup();
    }
}
