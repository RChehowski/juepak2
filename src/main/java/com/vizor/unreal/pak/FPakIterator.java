package com.vizor.unreal.pak;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class FPakIterator implements Iterator<PakIteratorEntry>
{
    private final Iterator<Map.Entry<String, FPakEntry>> iterator;
    private final FPakFile pakFile;

    FPakIterator(FPakFile pakFile)
    {
        final Map<String, FPakEntry> entries = Objects.requireNonNull(pakFile.index, "Index must be initialized");

        this.pakFile = pakFile;
        this.iterator = entries.entrySet().iterator();
    }

    @Override
    public boolean hasNext()
    {
        Objects.requireNonNull(iterator, "Iterator is null, unable to proceed");

        return iterator.hasNext();
    }

    @Override
    public final PakIteratorEntry next()
    {
        Objects.requireNonNull(iterator, "Iterator is null, unable to proceed");

        return new PakIteratorEntry(iterator.next(), pakFile);
    }
}
