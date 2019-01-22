package eu.chakhouski.juepak.pak;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class FFileIterator implements Iterator<FPakFile.Entry>
{
    private final Iterator<Map.Entry<String, FPakEntry>> iterator;
    private final FPakFile pakFile;

    FFileIterator(FPakFile pakFile)
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
    public final FPakFile.Entry next()
    {
        Objects.requireNonNull(iterator, getNoIteratorMessage());

        return new FPakFile.Entry(iterator.next(), pakFile);
    }


    private static String getNoIteratorMessage()
    {
        return "Iterator is null, unable to proceed";
    }
}
