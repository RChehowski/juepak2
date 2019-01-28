package eu.chakhouski.juepak.packer;

import java.util.zip.Deflater;

public final class PakEntryParameters
{
    private static PakEntryParameters defaultParameters;

    private boolean encrypt;
    private boolean deleteRecord;
    private boolean compress;

    public int deflaterHint = Deflater.DEFAULT_COMPRESSION;

    // Getter methods
    public boolean entryShouldBeEncrypted()
    {
        return encrypt;
    }

    public boolean entryIsDeleteRecord()
    {
        return deleteRecord;
    }

    public boolean entryShouldBeCompressed()
    {
        return compress;
    }

    // Builder methods:
    /**
     * Encrypts the record.
     *
     * @return Self.
     */
    public PakEntryParameters encrypt()
    {
        encrypt = true;
        return this;
    }

    /**
     * Compresses this entry.
     *
     * @return
     */
    public PakEntryParameters compress()
    {
        compress = true;
        return this;
    }

    /**
     * Mark this record as DeleteRecord.
     *
     * @return Self.
     */
    public PakEntryParameters deleteRecord()
    {
        throw new UnsupportedOperationException("This feature is unsupported");

//            deleteRecord = true;
//            return this;
    }

    /**
     * Sets a compression ratio for the {@link Deflater} instance,
     * only makes sense if {@link #compress()} selected.
     *
     * Accepts either:
     * - {@link Deflater#NO_COMPRESSION}
     * - {@link Deflater#BEST_SPEED}
     * - {@link Deflater#BEST_COMPRESSION}
     * - {@link Deflater#DEFAULT_COMPRESSION}
     *
     * @apiNote Default value is {@link Deflater#DEFAULT_COMPRESSION}
     * @param value New compression ratio.
     *
     * @return Self.
     */
    public PakEntryParameters deflaterHint(int value)
    {
        deflaterHint = value;
        return this;
    }


    public static PakEntryParameters sharedDefaultParameters()
    {
        if (defaultParameters == null)
        {
            defaultParameters = new PakEntryParameters();
        }

        return defaultParameters;
    }
}
