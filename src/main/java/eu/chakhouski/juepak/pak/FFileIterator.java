package eu.chakhouski.juepak.pak;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class FFileIterator implements Iterator<FPakFile.Entry>
{
    private final Iterator<Map.Entry<String, FPakEntry>> underlyingIterator;

    FFileIterator(FPakFile InPakFile)
    {
        final Map<String, FPakEntry> entries = InPakFile.GetEntries();

        underlyingIterator = entries.entrySet().iterator();
    }

    @Override
    public boolean hasNext()
    {
        Objects.requireNonNull(underlyingIterator, getNoIteratorMessage());

        return underlyingIterator.hasNext();
    }

    @Override
    public final FPakFile.Entry next()
    {
        Objects.requireNonNull(underlyingIterator, getNoIteratorMessage());

        return new FPakFile.Entry(underlyingIterator.next());
    }


    private static String getNoIteratorMessage()
    {
        return "Iterator is null, unable to proceed";
    }
}
