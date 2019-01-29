package com.vizor.unreal.pak;

import com.vizor.unreal.annotations.APIBridgeMethod;
import com.vizor.unreal.annotations.JavaDecoratorField;
import com.vizor.unreal.ue4.FAES;
import com.vizor.unreal.ue4.FCoreDelegates;
import com.vizor.unreal.ue4.FSHA1;
import com.vizor.unreal.ue4.FString;
import com.vizor.unreal.util.UE4Deserializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static com.vizor.unreal.util.Bool.BOOL;
import static com.vizor.unreal.util.Misc.toInt;

public class FPakFile implements Iterable<PakIteratorEntry>, AutoCloseable
{
    /**
     * Random seed used in {@link FPakFile#briefChecksumOfContent()}
     * Used to gain entropy in checksum computation.
     */
    private static final long INITIAL_RANDOM_SEED = System.nanoTime();

    /**
     * Pak filename.
     */
    private String pakFilename;

    /**
     * Pak file info (trailer).
     */
    private final FPakInfo info = new FPakInfo();

    /**
     * Mount point.
     */
    private String mountPoint;

    /**
     * TotalSize of the pak file.
     */
    private long cachedTotalSize;

    /**
     * Cached file input stream, closes in {@link #close()}
     */
    @JavaDecoratorField
    public FileInputStream inputStream;

    /**
     * True if this pak file is valid and usable.
     */
    private boolean bIsValid = false;

    /**
     * Map of entries.
     */
    final Map<String, FPakEntry> index = new LinkedHashMap<>();


    public FPakFile(final Path path)
    {
        try {
            pakFilename = path.toString();
            inputStream = new FileInputStream(path.toFile());

            Initialize(inputStream.getChannel());
        }
        catch (IOException ignore) {
            // Throw (maybe) wrapped into a RuntimeException.
        }
    }

    // === Constructor and destructor ===

    @Override
    public void close() throws IOException
    {
        if (inputStream != null)
        {
            inputStream.close();
            inputStream = null;
        }
    }

    private static String makeDirectoryFromPath(final String path)
    {
        final int length = path.length();
        if (length > 0 && path.charAt(length - 1) != '/')
        {
            return path + "/";
        }
        else
        {
            return path;
        }
    }

    private void decryptData(byte[] inData, int inDataSize)
    {
        final byte[] keyBytes = new byte[32];

        final FCoreDelegates.FPakEncryptionKeyDelegate delegate = FCoreDelegates.GetPakEncryptionKeyDelegate();
        if (delegate.IsBound())
        {
            delegate.Execute(keyBytes);
        }

        FAES.DecryptData(inData, inDataSize, keyBytes);

        // Nullify key bytes
        Arrays.fill(keyBytes, (byte) 0);
    }

    private void Initialize(SeekableByteChannel channel) throws IOException
    {
        cachedTotalSize = channel.size();
        boolean bShouldLoad = true;
        int compatibleVersion = FPakInfo.PakFile_Version_Latest;
        if (cachedTotalSize > 0)
        {
            while (compatibleVersion > 0 && (cachedTotalSize < info.GetSerializedSize(compatibleVersion)))
            {
                compatibleVersion--;
            }

            if (compatibleVersion < FPakInfo.PakFile_Version_Initial)
            {
                bShouldLoad = false;
            }
        }

        if (bShouldLoad)
        {
            final ByteBuffer map = ByteBuffer.allocate(toInt(info.GetSerializedSize(compatibleVersion)))
                    .order(ByteOrder.LITTLE_ENDIAN);

            // Serialize trailer and check if everything is as expected.
            channel.position(cachedTotalSize - info.GetSerializedSize(compatibleVersion));
            channel.read(map);

            info.Deserialize((ByteBuffer) map.flip(), compatibleVersion);

            if (info.Magic != FPakInfo.PakFile_Magic)
                throw new IOException("Trailing magic number " + info.Magic + " in " + pakFilename +
                        " is different than the expected one. Verify your installation.");

            if (!(info.Version >= FPakInfo.PakFile_Version_Initial && info.Version <= FPakInfo.PakFile_Version_Latest))
                throw new IOException("Invalid pak file version (" + info.Version + ") in " + pakFilename + ". Verify your installation.");

            if ((info.bEncryptedIndex == 1) && (!FCoreDelegates.GetPakEncryptionKeyDelegate().IsBound()))
                throw new IOException("Index of pak file '" + pakFilename + "' is encrypted, but this executable doesn't have any valid decryption keys");

            if (!(info.IndexOffset >= 0 && info.IndexOffset < cachedTotalSize))
                throw new IOException("Index offset for pak file '" + pakFilename + "' is invalid (" + info.IndexOffset + ")");

            if (!((info.IndexOffset + info.IndexSize) >= 0 && (info.IndexOffset + info.IndexSize) <= cachedTotalSize))
                throw new IOException("Index end offset for pak file '" + pakFilename + "' is invalid (" + info.IndexOffset + info.IndexSize + ")");

            if (!info.EncryptionKeyGuid.IsValid() /* || GRegisteredEncryptionKeys.HasKey(Info.EncryptionKeyGuid) */)
            {
                LoadIndex(channel);

                // LoadIndex should crash in case of an error, so just assume everything is ok if we got here.
                // Except that we won't crash.
                bIsValid = true;
            }
        }
    }

    private void LoadIndex(SeekableByteChannel channel) throws IOException
    {
        if (cachedTotalSize < (info.IndexOffset + info.IndexSize))
        {
            throw new IOException("Corrupted index offset in pak file.");
        }
        else
        {
            final ByteBuffer indexData = ByteBuffer.allocate(toInt(info.IndexSize)).order(ByteOrder.LITTLE_ENDIAN);

            final int actualReadBytes = channel.position(info.IndexOffset).read(indexData);
            if (actualReadBytes != info.IndexSize)
            {
                throw new IOException(String.join(System.lineSeparator(),
                        "Can not read that much index data from pak file channel",
                        "   index offset: " + info.IndexOffset,
                        "   index size  : " + info.IndexSize,
                        "   total bytes : " + channel.size(),
                        "   actual read : " + actualReadBytes
                ));
            }

            indexData.position(0);

            // Decrypt in-place if necessary
            if (BOOL(info.bEncryptedIndex))
            {
                decryptData(indexData.array(), (int) info.IndexSize);
            }

            // Check SHA1 value.
            byte[] indexHash = new byte[FSHA1.GetDigestLength()];
            FSHA1.HashBuffer(indexData.array(), indexData.capacity(), indexHash);

            if (!Arrays.equals(indexHash, info.IndexHash))
            {
                final String storedIndexHash = "0x" + FString.BytesToHex(info.IndexHash);
                final String computedIndexHash = "0x" + FString.BytesToHex(indexHash);

                throw new IOException(String.join(System.lineSeparator(),
                        "Corrupt pak index detected!",
                        " Filename: " + pakFilename,
                        " Encrypted: " + info.bEncryptedIndex,
                        " Total Size: " + cachedTotalSize,
                        " Index Offset: " + info.IndexOffset,
                        " Index Size: " + info.IndexSize,
                        " Stored Index Hash: " + storedIndexHash,
                        " Computed Index Hash: " + computedIndexHash,
                        "Corrupted index in pak file (CRC mismatch)."
                ));
            }

            // Read the default mount point and all entries.
            int numEntries;
            mountPoint = UE4Deserializer.Read(indexData, String.class);
            numEntries = UE4Deserializer.ReadInt(indexData);

            mountPoint = makeDirectoryFromPath(mountPoint);

            for (int entryIndex = 0; entryIndex < numEntries; entryIndex++)
            {
                // Deserialize from memory.
                // 1. First the file name (String)
                final String filename = UE4Deserializer.Read(indexData, String.class);

                // 2. And then, the entry
                final FPakEntry entry = new FPakEntry();
                entry.Deserialize(indexData, info.Version);

                // Put the entry
                index.put(filename, entry);
            }
        }
    }

    @Override
    public FPakIterator iterator()
    {
        assertValid();

        return new FPakIterator(this);
    }

    public final PakIteratorEntry[] getSortedEntries(final Comparator<PakIteratorEntry> comparator)
    {
        assertValid();

        final PakIteratorEntry[] entries = new PakIteratorEntry[index.size()];

        // Put into array
        int n = 0;
        for (final Map.Entry<String, FPakEntry> e : index.entrySet())
            entries[n++] = new PakIteratorEntry(e, this);

        Arrays.sort(entries, comparator);

        return entries;
    }

    /**
     * Calculates a SHA1 checksum based on XORed checksum of each entry.
     * This method is very fast and stable and it does not even tries to unpack any data.
     * <p>
     * This method is guaranteed to produce stable results every time it ran on identical files.
     *
     * @return 20 bytes of brief SHA1 checksum of the file.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    @APIBridgeMethod
    public final byte[] briefChecksumOfContent()
    {
        assertValid();

        // Perform direct allocation to speed-up bulk operations
        // Otherwise, java will fall into byte-merging and will make our bulk operations senseless
        final ByteBuffer itemBuffer = ByteBuffer.allocateDirect(FSHA1.GetDigestLength()).order(ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer mergeBuffer = ByteBuffer.allocateDirect(FSHA1.GetDigestLength()).order(ByteOrder.LITTLE_ENDIAN);

        // Salt generator, generating
        final Random saltGenerator = new Random(INITIAL_RANDOM_SEED);

        for (final Map.Entry<String, FPakEntry> entry : index.entrySet())
        {
            final FPakEntry pakEntry = entry.getValue();

            // Put hash
            itemBuffer.position(0);
            itemBuffer.put(pakEntry.Hash);

            // XOR data
            itemBuffer.position(0);
            mergeBuffer.position(0);

            // Perform 3 bulk operations to xor 8 + 8 + 4 = 20 bytes of SHA1
            final long l0 = mergeBuffer.getLong() ^ itemBuffer.getLong() ^ saltGenerator.nextLong();
            final long l1 = mergeBuffer.getLong() ^ itemBuffer.getLong() ^ saltGenerator.nextLong();
            final int i0 = mergeBuffer.getInt() ^ itemBuffer.getInt() ^ saltGenerator.nextInt();

            // Put back into mergeBuffer
            mergeBuffer.position(0);
            mergeBuffer.putLong(l0).putLong(l1).putInt(i0);
        }

        // Get result bytes from our DirectByteBuffer
        mergeBuffer.position(0);
        final byte[] result = new byte[mergeBuffer.capacity()];
        mergeBuffer.get(result);

        return result;
    }

    @APIBridgeMethod
    public final long getPayloadSize()
    {
        assertValid();

        long size = 0;
        for (final PakIteratorEntry e : this)
        {
            final FPakEntry pakEntry = e.Entry;
            size += pakEntry.Size;
        }

        return size;
    }

    @APIBridgeMethod
    public final long getPayloadUncompressedSize()
    {
        assertValid();

        long size = 0;
        for (final PakIteratorEntry e : this)
        {
            final FPakEntry pakEntry = e.Entry;
            size += pakEntry.UncompressedSize;
        }

        return size;
    }

    /**
     * Gets pak filename.
     *
     * @return Pak filename.
     */
    public final String getFilename()
    {
        assertValid();
        return pakFilename;
    }

    public final long totalSize()
    {
        assertValid();
        return cachedTotalSize;
    }

    public final FPakInfo getInfo()
    {
        assertValid();
        return info;
    }

    public void setMountPoint(String mountPoint)
    {
        assertValid();
        this.mountPoint = mountPoint;
    }

    public String getMountPoint()
    {
        assertValid();
        return mountPoint;
    }

    /**
     * Checks if the pak file is valid.
     *
     * @return true if this pak file is valid, false otherwise.
     */
    public boolean isValid()
    {
        return bIsValid;
    }

    @APIBridgeMethod
    public long getNumFiles()
    {
        assertValid();
        return index.size();
    }

    private void assertValid()
    {
        if (isValid())
        {
            throw new UnsupportedOperationException("Unable to perform operation, file is not valid");
        }
    }


    @Override
    public int hashCode()
    {
        if (isValid())
        {
            return Arrays.hashCode(briefChecksumOfContent());
        }

        return 0;
    }

    @Override
    public String toString()
    {
        if (isValid())
        {
            return "FPakFile{" +
                "pakFilename='" + pakFilename + '\'' +
                ", info=" + info +
                ", mountPoint='" + mountPoint + '\'' +
                ", cachedTotalSize=" + cachedTotalSize +
            '}';
        }

        return "File " + pakFilename + " is not valid";
    }

}
