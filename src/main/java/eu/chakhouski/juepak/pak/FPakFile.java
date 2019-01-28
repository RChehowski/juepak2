package eu.chakhouski.juepak.pak;

import eu.chakhouski.juepak.annotations.APIBridgeMethod;
import eu.chakhouski.juepak.annotations.JavaDecoratorField;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FCoreDelegates.FPakEncryptionKeyDelegate;
import eu.chakhouski.juepak.ue4.FSHA1;
import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.UE4Deserializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static eu.chakhouski.juepak.util.Bool.BOOL;
import static eu.chakhouski.juepak.util.Misc.toInt;

public class FPakFile implements Iterable<PakIteratorEntry>, AutoCloseable
{
    /**
     * Random seed used in {@link FPakFile#BriefChecksumOfContent()}
     */
    private static final long INITIAL_RANDOM_SEED = System.nanoTime();

    /**
     * Pak filename.
     */
    private final String pakFilename;

    /**
     * Pak file info (trailer).
     */
    private final FPakInfo Info = new FPakInfo();

    /**
     * Map of entries
     */
    private final Map<String, FPakEntry> Entries = new LinkedHashMap<>();

    /**
     * Mount point.
     */
    private String MountPoint;

    /**
     * Info on all files stored in pak.
     */
    private int NumEntries;

    /**
     * TotalSize of the pak file
     */
    private long CachedTotalSize;

    /**
     * Cached file input stream, closes in {@link #close()}
     */
    @JavaDecoratorField
    public FileInputStream inputStream;


    public FPakFile(final Path path) throws IOException
    {
        pakFilename = path.toString();
        inputStream = new FileInputStream(path.toFile());

        Initialize(inputStream.getChannel());
    }

    private static String MakeDirectoryFromPath(String Path)
    {
        if (Path.length() > 0 && Path.charAt(Path.length() - 1) != '/')
        {
            return Path + "/";
        }
        else
        {
            return Path;
        }
    }


    // === Constructor and destructor ===

    /**
     * Map of entries
     */
    Map<String, FPakEntry> GetEntries()
    {
        return Entries;
    }

    @Override
    public void close() throws IOException
    {
        if (inputStream != null)
        {
            inputStream.close();
            inputStream = null;
        }
    }

    public final FPakInfo GetInfo()
    {
        return Info;
    }

    private void DecryptData(byte[] InData, int InDataSize)
    {
        FPakEncryptionKeyDelegate Delegate = FCoreDelegates.GetPakEncryptionKeyDelegate();

        final byte[] keyBytes = new byte[32];
        if (Delegate.IsBound())
        {
            Delegate.Execute(keyBytes);
        }

        FAES.DecryptData(InData, InDataSize, keyBytes);

        // Nullify key bytes
        Arrays.fill(keyBytes, (byte) 0);
    }

    private void Initialize(SeekableByteChannel channel) throws IOException
    {
        CachedTotalSize = channel.size();

        final ByteBuffer map = ByteBuffer.allocate(toInt(Info.GetSerializedSize(Info.Version)))
                .order(ByteOrder.LITTLE_ENDIAN);

        final int FIXME_defaultVersion = FPakInfo.PakFile_Version_RelativeChunkOffsets;

        channel.position(CachedTotalSize - Info.GetSerializedSize(FIXME_defaultVersion));
        channel.read(map);
        map.flip();

        Info.Deserialize(map, FIXME_defaultVersion);

        if (CachedTotalSize < Info.GetSerializedSize())
        {
            if (BOOL(CachedTotalSize)) // UEMOB-425: can be zero - only error when not zero
            {
                throw new RuntimeException("Corrupted pak file " + pakFilename + " (too short). Verify your installation.");
            }
        }


        if (Info.Magic != FPakInfo.PakFile_Magic)
            throw new IOException("Trailing magic number " + Info.Magic + " in " + pakFilename +
                    " is different than the expected one. Verify your installation.");

        if (!(Info.Version >= FPakInfo.PakFile_Version_Initial && Info.Version <= FPakInfo.PakFile_Version_Latest))
            throw new IOException("Invalid pak file version (" + Info.Version + ") in " + pakFilename + ". Verify your installation.");

        if ((Info.bEncryptedIndex == 1) && (!FCoreDelegates.GetPakEncryptionKeyDelegate().IsBound()))
            throw new IOException("Index of pak file '" + pakFilename + "' is encrypted, but this executable doesn't have any valid decryption keys");

        if (!(Info.IndexOffset >= 0 && Info.IndexOffset < CachedTotalSize))
            throw new IOException("Index offset for pak file '" + pakFilename + "' is invalid (" + Info.IndexOffset + ")");

        if (!((Info.IndexOffset + Info.IndexSize) >= 0 && (Info.IndexOffset + Info.IndexSize) <= CachedTotalSize))
            throw new IOException("Index end offset for pak file '" + pakFilename + "' is invalid (" + Info.IndexOffset + Info.IndexSize + ")");

        LoadIndex(channel);
    }

    private void LoadIndex(SeekableByteChannel channel) throws IOException
    {
        if (CachedTotalSize < (Info.IndexOffset + Info.IndexSize))
        {
            throw new IOException("Corrupted index offset in pak file.");
        }
        else
        {
            final ByteBuffer IndexData = ByteBuffer.allocate(toInt(Info.IndexSize))
                    .order(ByteOrder.LITTLE_ENDIAN);

            final int actualReadBytes = channel.position(Info.IndexOffset).read(IndexData);
            if (actualReadBytes != Info.IndexSize)
            {
                throw new IOException(String.join(System.lineSeparator(),
                        "Can not read that much index data from pak file channel",
                        "   index offset: " + Info.IndexOffset,
                        "   index size  : " + Info.IndexSize,
                        "   total bytes : " + channel.size(),
                        "   actual read : " + actualReadBytes
                ));
            }

            IndexData.position(0);

            // Decrypt in-place if necessary
            if (BOOL(Info.bEncryptedIndex))
            {
                DecryptData(IndexData.array(), (int) Info.IndexSize);
            }

            // Check SHA1 value.
            byte[] IndexHash = new byte[FSHA1.GetDigestLength()];
            FSHA1.HashBuffer(IndexData.array(), IndexData.capacity(), IndexHash);

            if (!Arrays.equals(IndexHash, Info.IndexHash))
            {
                final String StoredIndexHash = "0x" + FString.BytesToHex(Info.IndexHash);
                final String ComputedIndexHash = "0x" + FString.BytesToHex(IndexHash);

                throw new IOException(String.join(System.lineSeparator(),
                        "Corrupt pak index detected!",
                        " Filename: " + pakFilename,
                        " Encrypted: " + Info.bEncryptedIndex,
                        " Total Size: " + CachedTotalSize,
                        " Index Offset: " + Info.IndexOffset,
                        " Index Size: " + Info.IndexSize,
                        " Stored Index Hash: " + StoredIndexHash,
                        " Computed Index Hash: " + ComputedIndexHash,
                        "Corrupted index in pak file (CRC mismatch)."
                ));
            }

            // Read the default mount point and all entries.
            NumEntries = 0;
            MountPoint = UE4Deserializer.Read(IndexData, String.class);
            NumEntries = UE4Deserializer.ReadInt(IndexData);

            MountPoint = MakeDirectoryFromPath(MountPoint);

            for (int EntryIndex = 0; EntryIndex < NumEntries; EntryIndex++)
            {
                // Deserialize from memory.
                // 1. First the file name (String)
                final String Filename = UE4Deserializer.Read(IndexData, String.class);

                // 2. And then, the entry
                final FPakEntry Entry = new FPakEntry();
                Entry.Deserialize(IndexData, Info.Version);

                // Put the entry
                Entries.put(Filename, Entry);
            }
        }
    }


    @Override
    public FPakIterator iterator()
    {
        return new FPakIterator(this);
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
    public final byte[] BriefChecksumOfContent()
    {
        // Perform direct allocation to speed-up bulk operations
        // Otherwise, java will fall into byte-merging and will make our bulk operations senseless
        final ByteBuffer ItemBuffer = ByteBuffer.allocateDirect(FSHA1.GetDigestLength())
                .order(ByteOrder.LITTLE_ENDIAN);

        final ByteBuffer MergeBuffer = ByteBuffer.allocateDirect(FSHA1.GetDigestLength())
                .order(ByteOrder.LITTLE_ENDIAN);

        // Salt generator, generating
        final Random saltGenerator = new Random(INITIAL_RANDOM_SEED);

        for (Map.Entry<String, FPakEntry> entry : Entries.entrySet())
        {
            final FPakEntry pakEntry = entry.getValue();

            // Put hash
            ItemBuffer.position(0);
            ItemBuffer.put(pakEntry.Hash);

            // XOR data
            ItemBuffer.position(0);
            MergeBuffer.position(0);

            // Perform 3 bulk operations to xor 8 + 8 + 4 = 20 bytes of SHA1
            final long l0 = MergeBuffer.getLong() ^ ItemBuffer.getLong() ^ saltGenerator.nextLong();
            final long l1 = MergeBuffer.getLong() ^ ItemBuffer.getLong() ^ saltGenerator.nextLong();
            final int i0 = MergeBuffer.getInt() ^ ItemBuffer.getInt() ^ saltGenerator.nextInt();

            // Put back into MergeBuffer
            MergeBuffer.position(0);
            MergeBuffer.putLong(l0).putLong(l1).putInt(i0);
        }

        // Get result bytes from our DirectByteBuffer
        MergeBuffer.position(0);
        final byte[] Result = new byte[MergeBuffer.capacity()];
        MergeBuffer.get(Result);

        return Result;
    }

    @APIBridgeMethod
    public final long getSize()
    {
        long size = 0;

        for (PakIteratorEntry e : this)
        {
            final FPakEntry pakEntry = e.Entry;
            size += pakEntry.Size;
        }

        return size;
    }

    @APIBridgeMethod
    public final long getUncompressedSize()
    {
        long size = 0;

        for (PakIteratorEntry e : this)
        {
            final FPakEntry pakEntry = e.Entry;
            size += pakEntry.UncompressedSize;
        }

        return size;
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(BriefChecksumOfContent());
    }

    @Override
    public String toString()
    {
        return "FPakFile{" +
            "pakFilename='" + pakFilename + '\'' +
            ", Info=" + Info +
            ", MountPoint='" + MountPoint + '\'' +
            ", NumEntries=" + NumEntries +
            ", CachedTotalSize=" + CachedTotalSize +
        '}';
    }

}
