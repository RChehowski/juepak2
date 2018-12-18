package eu.chakhouski.juepak.ue4;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FSHA1
{
    private static final MessageDigest Sha;

    static
    {
        try {
            Sha = MessageDigest.getInstance("SHA-1");
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
        Sha.reset();
        Sha.update(Data, 0, DataSize);

        try {
            Sha.digest(OutHash, 0, Sha.getDigestLength());
        }
        catch (DigestException e) {
            throw new RuntimeException(e);
        }
    }
}
