package com.vizor.unreal.ue4;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SuppressWarnings("SpellCheckingInspection")
public class FSHA1
{
    private static final MessageDigest Sha1;

    static
    {
        try {
            Sha1 = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculate the hash on a single block and return it
     *
     * @param Data Input data to hash
     * @param DataSize Size of the Data block
     * @param OutHash Resulting hash value (20 byte buffer)
     */
    public static void HashBuffer(byte[] Data, int DataSize, byte[] OutHash)
    {
        // do an atomic hash operation
        Sha1.reset();
        Sha1.update(Data, 0, DataSize);

        try {
            Sha1.digest(OutHash, 0, Sha1.getDigestLength());
        }
        catch (DigestException e) {
            throw new RuntimeException(e);
        }
    }

    public static int GetDigestLength()
    {
        return Sha1.getDigestLength();
    }
}
