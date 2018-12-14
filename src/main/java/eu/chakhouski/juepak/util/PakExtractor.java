package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FPakEntry;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;

public class PakExtractor
{
    public static void Extract(Path PathToPakFile, Map<String, FPakEntry> ItemsToExtract, Path RootPath)
    {
        final byte[] buffer = new byte[64 * 1024];

        try (final FileInputStream PakInputStream = new FileInputStream(PathToPakFile.toFile()))
        {
            final FileChannel Channel = PakInputStream.getChannel();

            for (Map.Entry<String, FPakEntry> MapEntry : ItemsToExtract.entrySet())
            {
                final String Filename = MapEntry.getKey();
                final FPakEntry Entry = MapEntry.getValue();

                final Path AbsolutePath = RootPath.resolve(Filename);
                final Path AbsoluteDir = AbsolutePath.getParent();

                if (!Files.isDirectory(AbsoluteDir))
                {
                    Files.createDirectories(AbsoluteDir);
                }

                Channel.position(Entry.Offset);

                long numBytesToRead = Entry.UncompressedSize;
                try (final FileOutputStream Fos = new FileOutputStream(AbsolutePath.toFile()))
                {
                    while (numBytesToRead > 0)
                    {
                        final long readPerOp = Math.min(numBytesToRead, buffer.length);

                        PakInputStream.read(buffer, 0, (int) readPerOp);
                        Fos.write(buffer, 0, (int) readPerOp);

                        numBytesToRead -= readPerOp;
                    }
                }
            }
        }
        catch (IOException e)
        {

        }
    }
}
