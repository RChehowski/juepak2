package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;
import eu.chakhouski.juepak.FPakInfo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class PakExtractor
{
    private static final FPakEntry CheckEntry = new FPakEntry();


    public synchronized static void Extract(FPakFile PakFile, String Filename, FPakEntry Entry, Path RootPath)
            throws IOException
    {
        final FPakInfo PakInfo = PakFile.Info;


        final FileInputStream PakInputStream = PakFile.InputStream;
        final FileChannel Channel = PakInputStream.getChannel();

        final Path AbsolutePath = RootPath.resolve(Filename);
        final Path AbsoluteDir = AbsolutePath.getParent();

        if (!Files.isDirectory(AbsoluteDir))
        {
            Files.createDirectories(AbsoluteDir);
        }

        final long EntrySerializedSize = PakFile.GetPakEntrySerializedSize();

        CheckEntry.Deserialize(
                Channel.map(FileChannel.MapMode.READ_ONLY, Entry.Offset, EntrySerializedSize),
                PakInfo.Version
        );

        // Compare entries
        if (Entry.equals(CheckEntry))
        {
            try (final FileOutputStream Fos = new FileOutputStream(AbsolutePath.toFile()))
            {
                Channel.transferTo(Entry.Offset + EntrySerializedSize, Entry.UncompressedSize, Fos.getChannel());
            }
        }
        else
        {
            throw new RuntimeException("Entry is invalid");
        }
    }
}
