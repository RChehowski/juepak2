package eu.chakhouski.juepak.pak;

import eu.chakhouski.juepak.annotations.APIBridgeMethod;
import eu.chakhouski.juepak.annotations.JavaDecoratorField;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FCoreDelegates.FPakEncryptionKeyDelegate;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FPaths;
import eu.chakhouski.juepak.ue4.FSHA1;
import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.UE4Deserializer;
import org.apache.commons.lang.mutable.MutableInt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static eu.chakhouski.juepak.util.Misc.BOOL;
import static eu.chakhouski.juepak.util.Misc.NULL;
import static eu.chakhouski.juepak.util.Misc.TEXT;
import static eu.chakhouski.juepak.util.Misc.toInt;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

@SuppressWarnings("StringConcatenationInLoop")
public class FPakFile implements Iterable<FPakEntry>, AutoCloseable
{
    /** Pak filename. */
    private final String PakFilename;

    /** Pak file info (trailer). */
    public final FPakInfo Info = new FPakInfo();
    /** Mount point. */
    private String MountPoint;
    /** Info on all files stored in pak. */
    private FPakEntry[] Files;
    /** Pak Index organized as a map of directories for faster Directory iteration. Valid only when bFilenamesRemoved == false. */
    Map<String, Map<String, FPakEntry>> Index = new HashMap<>();
    /** The number of file entries in the pak file */
    private int NumEntries;
    /** TotalSize of the pak file */
    private long CachedTotalSize;


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

        final byte[] keyBytes = new byte[20];
        if (Delegate.IsBound())
        {
            Delegate.Execute(keyBytes);
        }

        FAES.DecryptData(InData, InDataSize, keyBytes);

        // Nullify key bytes
        FMemory.Memset(keyBytes, 0, keyBytes.length);
    }

    /**
     * Gets pak file index.
     *
     * @return Pak index.
     */
    Map<String, Map<String, FPakEntry>> GetIndex()
    {
        return Index;
    }

    private void Initialize(SeekableByteChannel channel) throws IOException
    {
        CachedTotalSize = channel.size();


        final ByteBuffer map = ByteBuffer.allocate(toInt(Info.GetSerializedSize(Info.Version)))
                .order(ByteOrder.LITTLE_ENDIAN);

        channel.position(CachedTotalSize - Info.GetSerializedSize());
        channel.read(map);


        map.flip();

        Info.Deserialize(map, FPakInfo.PakFile_Version_Latest);

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
            throw new RuntimeException("Corrupted index offset in pak file.");
        }
        else
        {
            final ByteBuffer IndexData = ByteBuffer.allocate(toInt(Info.IndexSize))
                    .order(ByteOrder.LITTLE_ENDIAN);

            final int actualReadBytes = channel.position(Info.IndexOffset).read(IndexData);
            if (actualReadBytes != Info.IndexSize)
            {
                throw new IllegalArgumentException(String.join(System.lineSeparator(), Arrays.asList(
                    "Can not read that much index data from pak file channel",
                    "   index offset: " + Info.IndexOffset,
                    "   index size  : " + Info.IndexSize,
                    "   total bytes : " + channel.size(),
                    "   actual read : " + actualReadBytes
                )));
            }

            IndexData.position(0);

            // Decrypt in-place if necessary
            if (BOOL(Info.bEncryptedIndex))
            {
                DecryptData(IndexData.array(), (int) Info.IndexSize);
            }

            // Check SHA1 value.
            byte[] IndexHash = new byte[20];
            FSHA1.HashBuffer(IndexData.array(), IndexData.capacity(), IndexHash);
            if (FMemory.Memcmp(IndexHash, Info.IndexHash, sizeof(IndexHash)) != 0)
            {
                String StoredIndexHash, ComputedIndexHash;
                StoredIndexHash = TEXT("0x");
                ComputedIndexHash = TEXT("0x");

                for (int ByteIndex = 0; ByteIndex < 20; ++ByteIndex)
                {
                    StoredIndexHash += FString.Printf(TEXT("%02X"), Info.IndexHash[ByteIndex]);
                    ComputedIndexHash += FString.Printf(TEXT("%02X"), IndexHash[ByteIndex]);
                }

                throw new RuntimeException(String.join(System.lineSeparator(), Arrays.asList(
                    "Corrupt pak index detected!",
                    " Filename: " + PakFilename,
                    " Encrypted: " + Info.bEncryptedIndex,
                    " Total Size: " + CachedTotalSize,
                    " Index Offset: " + Info.IndexOffset,
                    " Index Size: " + Info.IndexSize,
                    " Stored Index Hash: " + StoredIndexHash,
                    " Computed Index Hash: " + ComputedIndexHash,
                    "Corrupted index in pak file (CRC mismatch)."
                )));
            }

            // Read the default mount point and all entries.
            NumEntries = 0;
            MountPoint = UE4Deserializer.Read(IndexData, String.class);
            NumEntries = UE4Deserializer.ReadInt(IndexData);

            MountPoint = MakeDirectoryFromPath(MountPoint);
            // Allocate enough memory to hold all entries (and not reallocate while they're being added to it).
            Files = new FPakEntry[NumEntries];

            for (int EntryIndex = 0; EntryIndex < NumEntries; EntryIndex++)
            {
                // Serialize from memory.
                final FPakEntry Entry = new FPakEntry();
                String Filename;
                Filename = UE4Deserializer.Read(IndexData, String.class);
                Entry.Deserialize(IndexData, Info.Version);

                // Add new file info.
                Files[EntryIndex] = Entry;

                // Construct Index of all directories in pak file.
                String Path = FPaths.GetPath(Filename);
                Path = MakeDirectoryFromPath(Path);
                Map<String, FPakEntry> Directory = Index.get(Path);
                if (Directory != NULL)
                {
                    Directory.put(FPaths.GetCleanFilename(Filename), Entry);
                }
                else
                {
                    Map<String, FPakEntry> NewDirectory = new HashMap<>();
                    NewDirectory.put(FPaths.GetCleanFilename(Filename), Entry);

                    Index.put(Path, NewDirectory);

                    // add the parent directories up to the mount point
                    while (!(MountPoint.equals(Path)))
                    {
                        Path = FString.Left(Path, Path.length() - 1);
                        MutableInt Offset = new MutableInt(0);
                        if (FString.FindLastChar(Path, '/', Offset))
                        {
                            Path = FString.Left(Path, Offset.intValue());
                            Path = MakeDirectoryFromPath(Path);
                            if (Index.get(Path) == NULL)
                            {
                                Index.put(Path, new HashMap<>());
                            }
                        }
                        else
                        {
                            Path = MountPoint;
                        }
                    }
                }
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
     * @return 20 bytes of brief SHA1 checksum of the file.
     */
    @SuppressWarnings("unused")
    @APIBridgeMethod
    public final byte[] BriefChecksumOfContent()
    {
        // Sort entries by offset to ensure stability
        final FPakEntry[] EntriesOffsetAscending = Arrays.copyOf(Files, Files.length);
        Arrays.sort(EntriesOffsetAscending, Comparator.comparingLong(Entry -> Entry.Offset));

        // Perform direct allocation to speed-up bulk operations
        // Otherwise, java will fall into byte-merging and will make our bulk operations senseless
        final ByteBuffer ItemBuffer = ByteBuffer.allocateDirect(20).order(ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer MergeBuffer = ByteBuffer.allocateDirect(20).order(ByteOrder.LITTLE_ENDIAN);

        for (final FPakEntry Entry : EntriesOffsetAscending)
        {
            ItemBuffer.position(0);
            ItemBuffer.put(Entry.Hash);

            // XOR data
            ItemBuffer.position(0);
            MergeBuffer.position(0);

            // Perform 3 bulk operations to xor 8+8+4=20 bytes of SHA1
            final long l0 = MergeBuffer.getLong() ^ ItemBuffer.getLong();
            final long l1 = MergeBuffer.getLong() ^ ItemBuffer.getLong();
            final int i0 = MergeBuffer.getInt() ^ ItemBuffer.getInt();

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
