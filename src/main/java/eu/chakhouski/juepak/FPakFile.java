package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FPaths;
import eu.chakhouski.juepak.util.Misc;
import eu.chakhouski.juepak.util.UE4Deserializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static eu.chakhouski.juepak.ue4.FPaths.INDEX_NONE;
import static eu.chakhouski.juepak.ue4.FStringUtils.Left;

public class FPakFile implements Iterable<FPakEntry>
{
    /** Pak filename. */
    private final String PakFilename;
    /** Pak file info (trailer). */
    public final FPakInfo Info = new FPakInfo();
    /** TotalSize of the pak file */
    private long CachedTotalSize;
    /** Mount point. */
    private String MountPoint;
    /** Info on all files stored in pak. */
    private final List<FPakEntry> Files = new ArrayList<>();
    /** Pak Index organized as a map of directories for faster Directory iteration. Valid only when bFilenamesRemoved == false. */
    Map<String, Map<String, FPakEntry>> Index = new HashMap<>();


    /** The number of file entries in the pak file */
    private int NumEntries;



    /**
     * Gets pak file index.
     *
     * @return Pak index.
     */
    Map<String, Map<String, FPakEntry>> GetIndex()
    {
        return Index;
    }


    public FPakFile(final String pakFilename)
    {
        PakFilename = pakFilename;

        try (FileInputStream fis = new FileInputStream(pakFilename)) {
            Initialize(fis.getChannel());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void Initialize(FileChannel channel) throws Exception
    {
        CachedTotalSize = channel.size();

        final MappedByteBuffer map = channel.map(
            MapMode.READ_ONLY,
            CachedTotalSize - Info.GetSerializedSize(),
            Info.GetSerializedSize()
        );

        Info.Deserialize(map.order(ByteOrder.LITTLE_ENDIAN));


        if (CachedTotalSize < Info.GetSerializedSize())
        {
            if (CachedTotalSize != 0) // UEMOB-425: can be zero - only error when not zero
            {
                throw new RuntimeException("Corrupted pak file " + PakFilename + " (too short). Verify your installation.");
            }
        }


        if (Info.Magic != FPakInfo.PakFile_Magic)
            throw new RuntimeException("Trailing magic number " + Info.Magic + " in " + PakFilename +
            " is different than the expected one. Verify your installation.");

        if (!(Info.Version >= FPakInfo.PakFile_Version_Initial && Info.Version <= FPakInfo.PakFile_Version_Latest))
            throw new RuntimeException("Invalid pak file version (" + Info.Version + ") in " + PakFilename + ". Verify your installation.");

        if ((Info.bEncryptedIndex == 1) /*&& (!FCoreDelegates::GetPakEncryptionKeyDelegate().IsBound()*/)
            throw new RuntimeException("Index of pak file '" + PakFilename + "' is encrypted, but this executable doesn't have any valid decryption keys");

        if (!(Info.IndexOffset >= 0 && Info.IndexOffset < CachedTotalSize))
            throw new RuntimeException("Index offset for pak file '" + PakFilename + "' is invalid (" + Info.IndexOffset + ")");

        if (!((Info.IndexOffset + Info.IndexSize) >= 0 && (Info.IndexOffset + Info.IndexSize) <= CachedTotalSize))
            throw new RuntimeException("Index end offset for pak file '" + PakFilename + "' is invalid (" + Info.IndexOffset + Info.IndexSize + ")");

        LoadIndex(channel);
    }

    private void LoadIndex(FileChannel channel) throws IOException, NoSuchAlgorithmException
    {
        final MappedByteBuffer IndexMapping = channel.map(MapMode.READ_ONLY, Info.IndexOffset, Info.IndexSize);
        IndexMapping.order(ByteOrder.LITTLE_ENDIAN);

        final byte[] IndexBytes = new byte[(int)Info.IndexSize];
        IndexMapping.get(IndexBytes);

        final byte[] IndexHash = MessageDigest.getInstance("SHA-1").digest(IndexBytes);

        if (!Arrays.equals(IndexHash, Info.IndexHash))
        {
            String StoredIndexHash, ComputedIndexHash;
            StoredIndexHash = "0x";
            ComputedIndexHash = "0x";

            StoredIndexHash += Misc.bytesToHex(Info.IndexHash);
            ComputedIndexHash += Misc.bytesToHex(IndexHash);

            throw new RuntimeException(String.join(System.lineSeparator(), Arrays.asList(
                "Corrupt pak index detected!",
                " Filename: " + PakFilename,
                " Encrypted: " + Info.bEncryptedIndex,
                " Total Size: " +  CachedTotalSize,
                " Index Offset: " + Info.IndexOffset,
                " Index Size: " + Info.IndexSize,
                " Stored Index Hash: " + StoredIndexHash,
                " Computed Index Hash: " + ComputedIndexHash,
                "Corrupted index in pak file (CRC mismatch)."
            )));
        }

        IndexMapping.position(0);
        MountPoint = UE4Deserializer.ReadString(IndexMapping);
        NumEntries = UE4Deserializer.ReadInt(IndexMapping);


        for (int EntryIndex = 0; EntryIndex < NumEntries; EntryIndex++)
        {
            // Serialize from memory.
            final FPakEntry Entry = new FPakEntry();
            String Filename;
            Filename = UE4Deserializer.ReadString(IndexMapping);
            Entry.Deserialize(IndexMapping, Info.Version);

            // Add new file info.
            Files.add(Entry);


            // Construct Index of all directories in pak file.
            String Path = FPaths.GetPath(Filename);
            Path = MakeDirectoryFromPath(Path);

            Map<String, FPakEntry> Directory = Index.get(Path);
            if (Directory != null)
            {
                Directory.put(FPaths.GetCleanFilename(Filename), Entry);
            }
            else
            {
                Map<String, FPakEntry> NewDirectory = new HashMap<>();
                Index.put(Path, NewDirectory);

                NewDirectory.put(FPaths.GetCleanFilename(Filename), Entry);


                // add the parent directories up to the mount point
                while (!(MountPoint.equals(Path)))
                {
                    Path = Left(Path, Path.length() - 1);

                    int Offset = Path.lastIndexOf('/');
                    if (Offset != INDEX_NONE)
                    {
                        Path = Left(Path, Offset);
                        Path = MakeDirectoryFromPath(Path);
                        if (!Index.containsKey(Path))
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

//            System.out.println(String.join(" ", Arrays.asList(
//                Filename,
//                "offset: " + Entry.Offset,
//                "size: " + Entry.Size + " bytes",
//                "sha1: " + Misc.bytesToHex(Entry.Hash)
//            )));
        }

        System.out.println("Hello");
    }



    @Override
    public FFileIterator iterator()
    {
        return new FFileIterator(this);
    }


    public static String MakeDirectoryFromPath(String Path)
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
}
