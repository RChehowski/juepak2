package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;
import eu.chakhouski.juepak.FPakInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.WritableByteChannel;

public class PakExtractor
{
    private static final FPakEntry CheckEntry = new FPakEntry();


    public synchronized static void Extract(FPakFile PakFile, FPakEntry Entry, WritableByteChannel ExtractionTarget)
            throws IOException
    {
        final FPakInfo PakInfo = PakFile.Info;
        final FileInputStream PakInputStream = PakFile.InputStream;

        // Might use cached channel if any has already created, this must be stable
        final FileChannel Channel = PakInputStream.getChannel();

        // Deserialize header once again
        final long EntrySerializedSize = PakFile.GetPakEntrySerializedSize();
        CheckEntry.Deserialize(Channel.map(MapMode.READ_ONLY, Entry.Offset, EntrySerializedSize), PakInfo.Version);

        // Compare entries
        if (Entry.equals(CheckEntry))
        {
            Channel.transferTo(Entry.Offset + EntrySerializedSize, Entry.UncompressedSize, ExtractionTarget);
        }
        else
        {
            throw new RuntimeException("Entry is invalid");
        }
    }
}
