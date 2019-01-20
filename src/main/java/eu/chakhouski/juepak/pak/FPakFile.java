package eu.chakhouski.juepak.pak;

import eu.chakhouski.juepak.annotations.APIBridgeMethod;
import eu.chakhouski.juepak.annotations.JavaDecoratorField;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FCoreDelegates.FPakEncryptionKeyDelegate;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FSHA1;
import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.UE4Deserializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static eu.chakhouski.juepak.util.Bool.BOOL;
import static eu.chakhouski.juepak.util.Misc.toInt;

@SuppressWarnings("StringConcatenationInLoop")
public class FPakFile implements Iterable<FPakFile.Entry>, AutoCloseable
{
    /** Map of entries */
    Map<String, FPakEntry> GetEntries()
    {
        return Entries;
    }

    void setEntries(Map<String, FPakEntry> entries)
    {
        Entries = entries;
    }

    public static class Entry
    {
        public final String Filename;
        public final FPakEntry Entry;


        public Entry(String filename, FPakEntry entry)
        {
            Filename = filename;
            Entry = entry;
        }

        public Entry(Map.Entry<? extends String, ? extends FPakEntry> mapEntry)
        {
            this(mapEntry.getKey(), mapEntry.getValue());
        }

        // *** API bridge ***
//        @APIBridgeMethod
//        public void extractMixed(String RootPath) throws IOException
//        {
//            extractMixed(Paths.get(RootPath));
//        }

//        @APIBridgeMethod
//        public void extractMixed(Path RootPath) throws IOException
//        {
//            final Path AbsolutePath = RootPath.resolve(Filename);
//            final Path AbsoluteDir = AbsolutePath.getParent();
//
//            // Create a directory if none yet
//            if (!Files.isDirectory(AbsoluteDir))
//            {
//                Files.createDirectories(AbsoluteDir);
//            }
//
//            // Extract to file channel
//            try (final FileOutputStream FileOS = new FileOutputStream(AbsolutePath.toFile()))
//            {
//                PakExtractor.Extract(PakFile, Entry, Channels.newChannel(FileOS));
//            }
//        }
//
//        @APIBridgeMethod
//        public void extractToMemory(final byte[] buffer) throws IOException
//        {
//            extractToMemory(buffer, 0);
//        }
//
//        @APIBridgeMethod
//        public void extractToMemory(final byte[] Buffer, final int Offset) throws IOException
//        {
//            // Perform fast check whether the drain can fit that much data
//            final int bufferCapacity = Buffer.length - Offset;
//            if (bufferCapacity < Entry.UncompressedSize)
//            {
//                throw new ArrayIndexOutOfBoundsException(
//                        "Your buffer of " + Buffer.length + " bytes starting from position " + Offset +
//                        " (total capacity of " + bufferCapacity + " bytes) can not fit current" +
//                        " pak entry (file) of " + Entry.UncompressedSize + " bytes"
//                );
//            }
//
//            // Do extract
//            PakExtractor.Extract(PakFile, Entry, Channels.newChannel(new OutputStream() {
//                int position = 0;
//
//                @Override
//                public void write(int b)
//                {
//                    Buffer[Offset + (position++)] = (byte)b;
//                }
//
//                @Override
//                public void write(byte[] InBuffer, int InBufferOffset, int InBufferLength)
//                {
//                    // the default write(int) fallback is too slow, we can instead copy bunches of bytes at once
//                    System.arraycopy(InBuffer, InBufferOffset, Buffer, Offset + position, InBufferLength);
//                    position += InBufferLength;
//                }
//            }));
//        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            final Entry entry = (Entry) o;
            return Objects.equals(Filename, entry.Filename) && Objects.equals(Entry, entry.Entry);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(Filename, Entry);
        }

        @Override
        public String toString()
        {
            return "Entry{Filename='" + Filename + "', Entry=" + Entry + '}';
        }
    }

    /**
     * Random seed used in {@link FPakFile#BriefChecksumOfContent()}
     */
    private static final long INITIAL_RANDOM_SEED = System.nanoTime();

    /** Pak filename. */
    private final String PakFilename;
    /** Pak file info (trailer). */
    private final FPakInfo Info = new FPakInfo();
    /** Mount point. */
    private String MountPoint;
    /** Info on all files stored in pak. */
    private int NumEntries;
    /** TotalSize of the pak file */
    private long CachedTotalSize;
    /** Map of entries */
    private Map<String, FPakEntry> Entries = new LinkedHashMap<>();

    /**
     * Cached file input stream, closes in {@link #close()}
     */
    @JavaDecoratorField
    public FileInputStream InputStream;


    // === Constructor and destructor ===

    public FPakFile(final String pakFilename) throws IOException
    {
        this(new File(pakFilename));
    }

    public FPakFile(final File file) throws IOException
    {
        PakFilename = file.getName();
        InputStream = new FileInputStream(file);

        Initialize(InputStream.getChannel());
    }

    @Override
    public void close() throws IOException
    {
        if (InputStream != null)
        {
            InputStream.close();
            InputStream = null;
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
        FMemory.Memset(keyBytes, 0, keyBytes.length);
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
                throw new RuntimeException("Corrupted pak file " + PakFilename + " (too short). Verify your installation.");
            }
        }


        if (Info.Magic != FPakInfo.PakFile_Magic)
            throw new IOException("Trailing magic number " + Info.Magic + " in " + PakFilename +
            " is different than the expected one. Verify your installation.");

        if (!(Info.Version >= FPakInfo.PakFile_Version_Initial && Info.Version <= FPakInfo.PakFile_Version_Latest))
            throw new IOException("Invalid pak file version (" + Info.Version + ") in " + PakFilename + ". Verify your installation.");

        if ((Info.bEncryptedIndex == 1) && (!FCoreDelegates.GetPakEncryptionKeyDelegate().IsBound()))
            throw new IOException("Index of pak file '" + PakFilename + "' is encrypted, but this executable doesn't have any valid decryption keys");

        if (!(Info.IndexOffset >= 0 && Info.IndexOffset < CachedTotalSize))
            throw new IOException("Index offset for pak file '" + PakFilename + "' is invalid (" + Info.IndexOffset + ")");

        if (!((Info.IndexOffset + Info.IndexSize) >= 0 && (Info.IndexOffset + Info.IndexSize) <= CachedTotalSize))
            throw new IOException("Index end offset for pak file '" + PakFilename + "' is invalid (" + Info.IndexOffset + Info.IndexSize + ")");

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
                    " Filename: " + PakFilename,
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
    public FFileIterator iterator()
    {
        return new FFileIterator(this);
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

    /**
     * Calculates a SHA1 checksum based on XORed checksum of each entry.
     * This method is very fast and stable and it does not even tries to unpack any data.
     *
     * This method is guaranteed to produce stable results every time it ran on identical files.
     *
     * @return 20 bytes of brief SHA1 checksum of the file.
     */
    @SuppressWarnings("unused")
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
            final int  i0 = MergeBuffer.getInt()  ^ ItemBuffer.getInt()  ^ saltGenerator.nextInt();

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
}
