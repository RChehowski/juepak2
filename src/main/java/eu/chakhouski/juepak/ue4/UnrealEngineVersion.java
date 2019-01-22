package eu.chakhouski.juepak.ue4;

import java.util.Objects;

public final class UnrealEngineVersion implements Comparable<UnrealEngineVersion>
{
    private final int maj;
    private final int min;
    private final int ptc;

    private static final UnrealEngineVersion minVersion
            = new UnrealEngineVersion(4, 0, 0);
    private static final UnrealEngineVersion maxVersion
            = new UnrealEngineVersion(4, Integer.MAX_VALUE, Integer.MAX_VALUE);

    UnrealEngineVersion(int maj, int min, int ptc)
    {
        this.maj = maj;
        this.min = min;
        this.ptc = ptc;

        validateEngineVersion(maj, min, ptc);
    }

    UnrealEngineVersion(String versionString)
    {
        final String[] split = versionString.split("\\.");

        if (split.length < 2)
        {
            throw new IllegalArgumentException("Unable to parse " + versionString +
                    " required format is \"x.y.z\" or \"x.y\"");
        }

        // Extract major, minVersion and patch versions
        this.maj = Integer.valueOf(split[0]);
        this.min = Integer.valueOf(split[1]);
        this.ptc = (split.length > 2) ? Integer.valueOf(split[2]) : 0;

        validateEngineVersion(maj, min, ptc);
    }

    @Override
    public int compareTo(UnrealEngineVersion o)
    {
        final int majCmp = Integer.compare(maj, o.maj);
        if (majCmp != 0)
            return majCmp;

        final int minCmp = Integer.compare(min, o.min);
        if (minCmp != 0)
            return minCmp;

        return Integer.compare(ptc, o.ptc);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (!(o instanceof UnrealEngineVersion))
            return false;

        return compareTo((UnrealEngineVersion) o) == 0;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(maj, min, ptc);
    }

    @Override
    public String toString()
    {
        return maj + "." + min + "." + ptc;
    }

    static UnrealEngineVersion minVersion()
    {
        return minVersion;
    }

    static UnrealEngineVersion maxVersion()
    {
        return maxVersion;
    }

    private static void validateEngineVersion(int maj, int min, int ptc)
    {
        if (maj != 4)
        {
            if (maj <= 0)
            {
                throw new IllegalArgumentException("Did the mankind finally realized how to turn back time? What " +
                        "is it? An UE" + maj + "? (only UE4 is supported, major version should be \"4\")");
            }
            else if (maj == 1)
            {
                throw new IllegalArgumentException("Rune, Deus Ex and the first Unreal Tournament were amazing... " +
                        "but the first Unreal Engine is a part of history (only UE4 is supported, major version should be \"4\")");
            }
            else if (maj == 2)
            {
                throw new IllegalArgumentException("Bioshock, Postal 2 and Splinter Cell were amazing... but UE2 " +
                        "is obsolete (only UE4 is supported, major version should be \"4\")");
            }
            else if (maj == 3)
            {
                throw new IllegalArgumentException("Mass Effect, Gears of War and Dishonored were amazing... but UE3 " +
                        "is a bit outdated (only UE4 is supported, major version should be \"4\")");
            }
            else
            {
                throw new IllegalArgumentException("You've got a time machine? I have no idea how to work with UE" +
                        maj + "... Will you teach me how? (only UE4 is supported, major version should be \"4\")");
            }
        }
        if (min < 0)
        {
            throw new IllegalArgumentException("Minor engine version can not be negative");
        }
        if (ptc < 0)
        {
            throw new IllegalArgumentException("Engine's patch version can not be negative");
        }
    }
}
