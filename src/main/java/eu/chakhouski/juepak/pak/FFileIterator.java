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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

public final class FFileIterator implements Iterator<FPakEntry>
{
    /** Owner pak file. */
    private final FPakFile PakFile;
    /** Index iterator. */
    private Iterator<Entry<String, Map<String, FPakEntry>>> IndexIt;
    /** Directory iterator. */
    private Iterator<Entry<String, FPakEntry>> DirectoryIt;

    // Cached iterator state
    private String CachedFilename;
    private String CachedDirectory;
    private FPakEntry CachedEntry;

    // Determines whether the hasNext() has been called
    private boolean JustWeaklyAdvanced = false;


    FFileIterator(FPakFile InPakFile)
    {
        PakFile = InPakFile;

        final Map<String, Map<String, FPakEntry>> Index = PakFile.GetIndex();
        IndexIt = Index.entrySet().iterator();

        DirectoryIt = Collections.emptyIterator();
    }

    private boolean Advance(boolean Weak)
    {
        // Return immediately advance is weak and if node is valid
        // called from hasNext() it prevents an iterator from being advanced once again
        if (Weak && JustWeaklyAdvanced)
        {
            return true;
        }

        // Try advance directory iterator
        if (DirectoryIt.hasNext())
        {
            DoAdvance();
            return true;
        }
        else
        {
            // Discard directory iterator, it is depleted
            DirectoryIt = null;

            // Try advance index iterator
            while (IndexIt.hasNext())
            {
                final Entry<String, Map<String, FPakEntry>> CurrentIndex = IndexIt.next();

                CachedDirectory = CurrentIndex.getKey();
                final Map<String, FPakEntry> PakEntries = CurrentIndex.getValue();

                DirectoryIt = PakEntries.entrySet().iterator();

                // Try advance directory iterator
                if (DirectoryIt.hasNext())
                {
                    DoAdvance();
                    return true;
                }
            }
        }

        return false;
    }

    private void DoAdvance()
    {
        final Entry<String, FPakEntry> DirectoryEntry = DirectoryIt.next();

        // Update cached Filename
        CachedFilename = CachedDirectory + DirectoryEntry.getKey();

        // Update cached Entry
        CachedEntry = DirectoryEntry.getValue();

        JustWeaklyAdvanced = true;
    }

    // *** Iterator interface ***
    @Override
    public final boolean hasNext()
    {
        return Advance(true);
    }

    @Override
    public final FPakEntry next()
    {
        // Check whether the iterator was weakly advanced with hasNext() method
        if (!JustWeaklyAdvanced)
        {
            // Added a variable to debug this code easily, i'm not quite sure if this is correct
            final boolean bStrongAdvanceFailed = !Advance(false);

            if (bStrongAdvanceFailed)
            {
                throw new NoSuchElementException("Iterator depleted");
            }
        }

        // Anyway, invalidate we
        JustWeaklyAdvanced = false;
        return PakEntry();
    }

    // *** Additional public API ***
    public final String Filename()
    {
        return CachedFilename;
    }

    public final FPakEntry PakEntry()
    {
        return CachedEntry;
    }

    // *** API bridge ***
    @APIBridgeMethod
    public void extractMixed(String RootPath) throws IOException
    {
        extractMixed(Paths.get(RootPath));
    }

    @APIBridgeMethod
    public void extractMixed(Path RootPath) throws IOException
    {
        final Path AbsolutePath = RootPath.resolve(Filename());
        final Path AbsoluteDir = AbsolutePath.getParent();

        // Create a directory if none yet
        if (!Files.isDirectory(AbsoluteDir))
        {
            Files.createDirectories(AbsoluteDir);
        }

        // Extract to file channel
        try (final FileOutputStream FileOS = new FileOutputStream(AbsolutePath.toFile()))
        {
            PakExtractor.Extract(PakFile, PakEntry(), Channels.newChannel(FileOS));
        }
    }

    @APIBridgeMethod
    public void extractToMemory(final byte[] buffer) throws IOException
    {
        extractToMemory(buffer, 0);
    }

    @APIBridgeMethod
    public void extractToMemory(final byte[] Buffer, final int Offset) throws IOException
    {
        // Check whether our buffer can potentially fit our data
        final FPakEntry PakEntry = PakEntry();

        // Perform fast check whether the drain can fit that much data
        final int bufferCapacity = Buffer.length - Offset;
        if (bufferCapacity < PakEntry.UncompressedSize)
        {
            throw new ArrayIndexOutOfBoundsException(
                "Your buffer of " + Buffer.length + " bytes starting from position " + Offset +
                " (total capacity of " + bufferCapacity + " bytes) can not fit current" +
                " pak entry (file) of " + PakEntry.UncompressedSize + " bytes"
            );
        }

        // Do extract
        PakExtractor.Extract(PakFile, PakEntry, Channels.newChannel(new OutputStream() {
            int position = 0;

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
        }));
    }

    @Override
    public String toString()
    {
        return "Filename: " + Filename() + ", Entry: " + PakEntry();
    }
}
