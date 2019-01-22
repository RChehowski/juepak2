package eu.chakhouski.juepak.util;

import java.util.Objects;

public class Range<T extends Comparable<T>>
{
    private final T lowerBound;
    private final T upperBound;

    public Range(final T lowerBound, final T upperBound)
    {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean contains(final T item, final boolean lowerInclusive, final boolean upperInclusive)
    {
        if ((lowerBound == null) || (upperBound == null) || (item == null))
            return false;

        final int cmpLower = lowerBound.compareTo(item);
        final int cmpUpper = upperBound.compareTo(item);

        return (lowerInclusive ? (cmpLower <= 0) : (cmpLower < 0)) &&
               (upperInclusive ? (cmpUpper >= 0) : (cmpUpper > 0));
    }

    public boolean contains(final T item)
    {
        return contains(item, true, false);
    }

    @SuppressWarnings("unused")
    public final T getLowerBound()
    {
        return lowerBound;
    }

    @SuppressWarnings("unused")
    public final T getUpperBound()
    {
        return upperBound;
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (!(o instanceof Range))
            return false;

        final Range<?> other = (Range<?>) o;
        return Objects.equals(lowerBound, other.lowerBound) && Objects.equals(upperBound, other.upperBound);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lowerBound, upperBound);
    }
}
