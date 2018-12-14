package eu.chakhouski.juepak;

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
import java.util.List;

public class FPakPlatformFile
{
    /** Pak filename. */
    private final String PakFilename;
    /** Pak file info (trailer). */
    private final FPakInfo Info = new FPakInfo();
    /** TotalSize of the pak file */
    private long CachedTotalSize;
    /** Mount point. */
    private String MountPoint;
    /** Info on all files stored in pak. */
    private final List<FPakEntry> Files = new ArrayList<>();
    /** The number of file entries in the pak file */
    private int NumEntries;


    public FPakPlatformFile(final String pakFilename)
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
        final MappedByteBuffer IndexMap = channel.map(MapMode.READ_ONLY, Info.IndexOffset, Info.IndexSize);
        IndexMap.order(ByteOrder.LITTLE_ENDIAN);

        final byte[] IndexBytes = new byte[(int)Info.IndexSize];
        IndexMap.get(IndexBytes);

        final byte[] IndexHash = MessageDigest.getInstance("SHA-1").digest(IndexBytes);

        if (!Arrays.equals(IndexHash, Info.IndexHash))
            throw new RuntimeException("Corrupt index (SHA-1 mismatch)");


        IndexMap.position(0);
        MountPoint = UE4Deserializer.ReadString(IndexMap);
        NumEntries = UE4Deserializer.ReadInt(IndexMap);


        for (int EntryIndex = 0; EntryIndex < NumEntries; EntryIndex++)
        {
            // Serialize from memory.
            final FPakEntry Entry = new FPakEntry();
            String Filename;
            Filename = UE4Deserializer.ReadString(IndexMap);
            Entry.Deserialize(IndexMap, Info.Version);

            // Add new file info.
            Files.add(Entry);

            System.out.println(String.join(" ", Arrays.asList(
                Filename,
                "offset: " + Entry.Offset,
                "size: " + Entry.Size + " bytes",
                "sha1: " + Misc.bytesToHex(Entry.Hash)
            )));
        }
    }





    public static String  MakeDirectoryFromPath(String Path)
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
