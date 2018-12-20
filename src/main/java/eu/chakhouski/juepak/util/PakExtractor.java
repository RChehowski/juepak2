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
    private static final byte[] ExtractBuffer = new byte[256 * 1024];


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


        final FPakEntry CheckEntry = new FPakEntry();
        final long EntrySerializedSize = CheckEntry.GetSerializedSize(PakInfo.Version);

        CheckEntry.Deserialize(
                Channel.map(FileChannel.MapMode.READ_ONLY, Entry.Offset, EntrySerializedSize),
                PakInfo.Version
        );

        // Compare entries
        if (!Entry.equals(CheckEntry))
        {
            throw new RuntimeException("Entry is invalid!");
        }

        // !!! Note that we need to skip the freaking FPakEntry once again !!!
        Channel.position(Entry.Offset + EntrySerializedSize);

        long numBytesToRead = Entry.UncompressedSize;
        try (final FileOutputStream Fos = new FileOutputStream(AbsolutePath.toFile()))
        {
            while (numBytesToRead > 0)
            {
                final long readPerOp = Math.min(numBytesToRead, ExtractBuffer.length);

                PakInputStream.read(ExtractBuffer, 0, (int) readPerOp);
                Fos.write(ExtractBuffer, 0, (int) readPerOp);

                numBytesToRead -= readPerOp;
            }
        }
    }
}
