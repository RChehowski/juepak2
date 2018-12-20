package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.APIBridgeMethod;
import eu.chakhouski.juepak.util.PakExtractor;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public final class FFileIterator implements Iterator<FPakEntry>
{
    /** Owner pak file. */
    private final FPakFile PakFile;
    /** Index iterator. */
    private Iterator<Entry<String, Map<String, FPakEntry>>> IndexIt;
    /** Directory iterator. */
    private Iterator<Entry<String, FPakEntry>> DirectoryIt;
    /** The cached filename for return in Filename() */
    private String CachedFilename;

    /**
     * Since in java {@link Iterator#next()} actually advances the iterator, we need to cache the current directory name.
     */
    private String CurrentDirectory;

    private FPakEntry CachedPakEntry;


    FFileIterator(FPakFile InPakFile)
    {
        PakFile = InPakFile;

        final Map<String, Map<String, FPakEntry>> Index = PakFile.GetIndex();
        IndexIt = Index.entrySet().iterator();

        DirectoryIt = Collections.emptyIterator();
    }


    @Override
    public final boolean hasNext()
    {
        return IndexIt.hasNext() || DirectoryIt.hasNext();
    }

    @Override
    public final FPakEntry next()
    {
        if (DirectoryIt.hasNext())
        {
            return GetPakEntry();
        }
        else
        {
            while (IndexIt.hasNext())
            {
                final Entry<String, Map<String, FPakEntry>> CurrentIndex = IndexIt.next();

                CurrentDirectory = CurrentIndex.getKey();
                final Map<String, FPakEntry> PakEntries = CurrentIndex.getValue();

                DirectoryIt = PakEntries.entrySet().iterator();
                if (DirectoryIt.hasNext())
                {
                    return GetPakEntry();
                }
            }

            throw new UnsupportedOperationException("Iterator is depleted");
        }
    }

    public final String Filename()
    {
        return CachedFilename;
    }

    private FPakEntry GetPakEntry()
    {
        final Entry<String, FPakEntry> DirectoryEntry = DirectoryIt.next();
        CachedFilename = CurrentDirectory + DirectoryEntry.getKey();

        // Update cached Pak Entry
        CachedPakEntry = DirectoryEntry.getValue();

        // Finally return
        return CachedPakEntry;
    }


    @APIBridgeMethod
    public void extractMixed(String RootPath) throws IOException
    {
        extractMixed(Paths.get(RootPath));
    }

    @APIBridgeMethod
    public void extractMixed(Path RootPath) throws IOException
    {
        final Path AbsolutePath = RootPath.resolve(CachedFilename);
        final Path AbsoluteDir = AbsolutePath.getParent();

        // Create a directory if none yet
        if (!Files.isDirectory(AbsoluteDir))
            Files.createDirectories(AbsoluteDir);

        // Extract to file channel
        try (final FileOutputStream Fos = new FileOutputStream(AbsolutePath.toFile()))
        {
            PakExtractor.Extract(PakFile, CachedPakEntry, Channels.newChannel(Fos));
        }
    }
}
