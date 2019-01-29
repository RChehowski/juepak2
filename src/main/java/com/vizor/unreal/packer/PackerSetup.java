package com.vizor.unreal.packer;

import com.vizor.unreal.pak.FPakInfo;
import com.vizor.unreal.util.PakVersion;

import java.nio.file.Path;

/**
 * A builder used to construct a packer.
 */
public final class PackerSetup
{
    private boolean encryptIndex = false;

    private int pakVersion = FPakInfo.PakFile_Version_Latest;
    private String customMountPoint = null;
    private Path archivePath = null;

    public PackerSetup()
    {
    }

    // Getters
    public boolean pakIndexShouldBeEncrypted()
    {
        return encryptIndex;
    }

    public int getPakVersion()
    {
        return pakVersion;
    }

    public boolean hasCustomMountPoint()
    {
        return customMountPoint != null;
    }

    public String getCustomMountPoint()
    {
        return customMountPoint;
    }

    public Path getArchivePath()
    {
        return archivePath;
    }

    // Builder methods
    public PackerSetup encryptIndex(boolean value)
    {
        encryptIndex = value;
        return this;
    }

    public PackerSetup engineVersion(String value)
    {
        pakVersion = PakVersion.getByEngineVersion(value);
        return this;
    }

    public PackerSetup customMountPoint(String value)
    {
        customMountPoint = value;
        return this;
    }

    public PackerSetup archiveFile(Path value)
    {
        archivePath = value;
        return this;
    }

    public Packer build()
    {
        // Check whether the user requested index encryption but the feature is not supported.
        if ((pakVersion < FPakInfo.PakFile_Version_IndexEncryption) && encryptIndex)
        {
            throw new IllegalStateException("Unable to encrypt index, feature is not supported. Pak version is " +
                    FPakInfo.pakFileVersionToString(pakVersion));
        }

        return new Packer(this);
    }
}
