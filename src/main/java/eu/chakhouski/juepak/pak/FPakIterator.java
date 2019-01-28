package eu.chakhouski.juepak.pak;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class FPakIterator implements Iterator<PakIteratorEntry>
{
    private final Iterator<Map.Entry<String, FPakEntry>> iterator;
    private final FPakFile pakFile;

    FPakIterator(FPakFile pakFile)
    {
        final Map<String, FPakEntry> entries = pakFile.GetEntries();

        this.pakFile = pakFile;
        this.iterator = entries.entrySet().iterator();
    }

    @Override
    public boolean hasNext()
    {
        Objects.requireNonNull(iterator, getNoIteratorMessage());

        return iterator.hasNext();
    }

    @Override
    public final PakIteratorEntry next()
    {
        Objects.requireNonNull(iterator, getNoIteratorMessage());

        return new PakIteratorEntry(iterator.next(), pakFile);
    }


    private static String getNoIteratorMessage()
    {
        return "Iterator is null, unable to proceed";
    }
}
