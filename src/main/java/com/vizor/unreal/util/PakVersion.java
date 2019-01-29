package com.vizor.unreal.util;

import com.vizor.unreal.pak.FPakInfo;

import java.util.LinkedHashMap;
import java.util.Map;

public class PakVersion
{
    @SuppressWarnings("SameParameterValue")
    private static UnrealEngineVersion version(int maj, int min, int ptc)
    {
        return new UnrealEngineVersion(maj, min, ptc);
    }

    private static <T extends Comparable<T>> Range<T> range(T lower, T upper)
    {
        return new Range<>(lower, upper);
    }

    private static final Map<Range<UnrealEngineVersion>, Integer> versionRanges = new LinkedHashMap<>();

    static
    {
        // Lower limit, returns invalid (-1) version
        versionRanges.put(range(UnrealEngineVersion.minVersion(), version(4,0,0)), -1);

        // Ranges
        versionRanges.put(range(version(4,0,0),  version(4,3,0)),
                FPakInfo.PakFile_Version_NoTimestamps);
        versionRanges.put(range(version(4,3,0),  version(4,16,0)),
                FPakInfo.PakFile_Version_CompressionEncryption);
        versionRanges.put(range(version(4,16,0), version(4,20,0)),
                FPakInfo.PakFile_Version_IndexEncryption);
        versionRanges.put(range(version(4,20,0), version(4,21,0)),
                FPakInfo.PakFile_Version_RelativeChunkOffsets);

        // Add newer version when they will be released

        // Currently the latest version
        versionRanges.put(range(version(4,21,0), UnrealEngineVersion.maxVersion()),
                FPakInfo.PakFile_Version_Latest);
    }


    public static int getByEngineVersion(String versionString)
    {
        final UnrealEngineVersion v = new UnrealEngineVersion(versionString);

        for (Map.Entry<Range<UnrealEngineVersion>, Integer> entry : versionRanges.entrySet())
        {
            if (entry.getKey().contains(v))
                return entry.getValue();
        }

        return FPakInfo.PakFile_Version_Invalid;
    }
}
