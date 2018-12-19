package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FPaths;
import eu.chakhouski.juepak.ue4.FSHA1;
import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.UE4Deserializer;
import org.apache.commons.lang.mutable.MutableInt;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static eu.chakhouski.juepak.util.Misc.BOOL;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

@SuppressWarnings("StringConcatenationInLoop")
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

    /**
     * Precaching
     */

    public void GetPakEncryptionKey(FAES.FAESKey OutKey)
    {
        FCoreDelegates.FPakEncryptionKeyDelegate Delegate = FCoreDelegates.GetPakEncryptionKeyDelegate();
        if (Delegate.IsBound())
        {
            Delegate.Execute(OutKey.Key);
        }
        else
        {
            FMemory.Memset(OutKey.Key, 0, sizeof(OutKey.Key));
        }
    }

    private void DecryptData(byte[] InData, long InDataSize)
    {
        try
        {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

//            cipher.init(Cipher.DECRYPT_MODE, );
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException e)
        {
            e.printStackTrace();
        }

//        SCOPE_SECONDS_ACCUMULATOR(STAT_PakCache_DecryptTime);
//        FAES::FAESKey Key;
//        FPakPlatformFile::GetPakEncryptionKey(Key);
//        FAES::DecryptData(InData, InDataSize, Key);
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
            if (BOOL(CachedTotalSize)) // UEMOB-425: can be zero - only error when not zero
            {
                throw new RuntimeException("Corrupted pak file " + PakFilename + " (too short). Verify your installation.");
            }
        }


        if (Info.Magic != FPakInfo.PakFile_Magic)
            throw new RuntimeException("Trailing magic number " + Info.Magic + " in " + PakFilename +
            " is different than the expected one. Verify your installation.");

        if (!(Info.Version >= FPakInfo.PakFile_Version_Initial && Info.Version <= FPakInfo.PakFile_Version_Latest))
            throw new RuntimeException("Invalid pak file version (" + Info.Version + ") in " + PakFilename + ". Verify your installation.");

        if ((Info.bEncryptedIndex == 1) && (!FCoreDelegates.GetPakEncryptionKeyDelegate().IsBound()))
            throw new RuntimeException("Index of pak file '" + PakFilename + "' is encrypted, but this executable doesn't have any valid decryption keys");

        if (!(Info.IndexOffset >= 0 && Info.IndexOffset < CachedTotalSize))
            throw new RuntimeException("Index offset for pak file '" + PakFilename + "' is invalid (" + Info.IndexOffset + ")");

        if (!((Info.IndexOffset + Info.IndexSize) >= 0 && (Info.IndexOffset + Info.IndexSize) <= CachedTotalSize))
            throw new RuntimeException("Index end offset for pak file '" + PakFilename + "' is invalid (" + Info.IndexOffset + Info.IndexSize + ")");

        LoadIndex(channel);
    }

    private void LoadIndex(FileChannel channel) throws IOException
    {
        // TODO: Deserialize bytes directly
        // Load index into memory first.
        final MappedByteBuffer IndexMapping = channel.map(MapMode.READ_ONLY, Info.IndexOffset, Info.IndexSize);
        IndexMapping.order(ByteOrder.LITTLE_ENDIAN);

        final byte[] IndexData;
        IndexData = new byte[(int)Info.IndexSize];
        IndexMapping.get(IndexData);

        // Decrypt if necessary
        if (BOOL(Info.bEncryptedIndex))
        {
//            DecryptData(IndexData.GetData(), Info.IndexSize);

            throw new RuntimeException("Encrypted index is not implemented yet");
        }

        // Check SHA1 value.
        byte[] IndexHash = new byte[20];
        FSHA1.HashBuffer(IndexData, IndexData.length, IndexHash);

        if (FMemory.Memcmp(IndexHash, Info.IndexHash, sizeof(IndexHash)) != 0)
        {
            String StoredIndexHash, ComputedIndexHash;
            StoredIndexHash = "0x";
            ComputedIndexHash = "0x";

            for (int ByteIndex = 0; ByteIndex < 20; ++ByteIndex)
            {
                StoredIndexHash += FString.Printf("%02X", Info.IndexHash[ByteIndex]);
                ComputedIndexHash += FString.Printf("%02X", IndexHash[ByteIndex]);
            }

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
        }
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
