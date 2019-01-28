package eu.chakhouski.juepak.pak;

import eu.chakhouski.juepak.annotations.APIBridgeMethod;
import eu.chakhouski.juepak.util.PakExtractor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.DoubleConsumer;

public class PakIteratorEntry
{
    /**
     * File name within the archive. Accessible from outside.
     */
    public final String Filename;

    /**
     * Pak entry in the archive. Accessible from outside.
     */
    public final FPakEntry Entry;

    /**
     * Cached pak file.
     */
    private final FPakFile pakFile;

    PakIteratorEntry(Map.Entry<? extends String, ? extends FPakEntry> mapEntry, FPakFile pakFile)
    {
        Filename = mapEntry.getKey();
        Entry = mapEntry.getValue();

        this.pakFile = pakFile;
    }

    @APIBridgeMethod
    public void extractMixed(String RootPath, DoubleConsumer progressConsumer) throws IOException
    {
        extractMixed(Paths.get(RootPath), progressConsumer);
    }

    @APIBridgeMethod
    public void extractMixed(Path RootPath, DoubleConsumer progressConsumer) throws IOException
    {
        final Path AbsolutePath = RootPath.resolve(Filename);
        final Path AbsoluteDir = AbsolutePath.getParent();

        // Create a directory if none yet
        if (!Files.isDirectory(AbsoluteDir))
        {
            Files.createDirectories(AbsoluteDir);
        }

        // Extract to file channel
        try (final FileOutputStream FileOS = new FileOutputStream(AbsolutePath.toFile()))
        {
            PakExtractor.Extract(pakFile, Entry, Channels.newChannel(FileOS), progressConsumer);
        }
    }

    @APIBridgeMethod
    public void extractToMemory(final byte[] buffer, DoubleConsumer progressConsumer) throws IOException
    {
        extractToMemory(buffer, 0, progressConsumer);
    }

    @APIBridgeMethod
    public void extractToMemory(final byte[] Buffer, final int Offset, DoubleConsumer progressConsumer)
            throws IOException
    {
        // Perform fast check whether the drain can fit that much data
        final int bufferCapacity = Buffer.length - Offset;
        if (bufferCapacity < Entry.UncompressedSize)
        {
            throw new ArrayIndexOutOfBoundsException(
                    "Your buffer of " + Buffer.length + " bytes starting from position " + Offset +
                    " (total capacity of " + bufferCapacity + " bytes) can not fit current" +
                    " pak entry (file) of " + Entry.UncompressedSize + " bytes"
            );
        }

        // Do extract
        PakExtractor.Extract(pakFile, Entry, Channels.newChannel(new OutputStream() {
            private int position = 0;

            @Override
            public void write(int b)
            {
                Buffer[Offset + (position++)] = (byte)b;
            }

            @Override
            public void write(byte[] InBuffer, int InBufferOffset, int InBufferLength)
            {
                // the default write(int) fallback is too slow, we can instead copy bunches of bytes at once
                System.arraycopy(InBuffer, InBufferOffset, Buffer, Offset + position, InBufferLength);
                position += InBufferLength;
            }
        }), progressConsumer);
    }
}
