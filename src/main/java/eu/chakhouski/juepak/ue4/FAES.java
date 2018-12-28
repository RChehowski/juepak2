package eu.chakhouski.juepak.ue4;

import eu.chakhouski.juepak.annotations.FStruct;
import eu.chakhouski.juepak.annotations.StaticSize;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static eu.chakhouski.juepak.util.Misc.TEXT;
import static eu.chakhouski.juepak.util.Misc.checkf;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

@SuppressWarnings("SpellCheckingInspection")
public class FAES
{
    private static final Cipher AES256Cipher;

    private static final int AES_KEYBITS = 256;
    public static final int AESBlockSize = 16;

    private static final byte[] EncryptionDecryptionBuffer = new byte[AESBlockSize];


    private static int KEYLENGTH(int keybits) { return ((keybits) / 8); }

    private static void fixKeyLength()
    {
        final String errorString = "Failed manually overriding key-length permissions.";

        int newMaxKeyLength;
        try
        {
            if ((newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES")) < 256)
            {
                Class<?> c = Class.forName("javax.crypto.CryptoAllPermissionCollection");
                Constructor con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissionCollection = con.newInstance();
                Field f = c.getDeclaredField("all_allowed");
                f.setAccessible(true);
                f.setBoolean(allPermissionCollection, true);

                c = Class.forName("javax.crypto.CryptoPermissions");
                con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissions = con.newInstance();
                f = c.getDeclaredField("perms");
                f.setAccessible(true);
                ((Map) f.get(allPermissions)).put("*", allPermissionCollection);

                c = Class.forName("javax.crypto.JceSecurityManager");
                f = c.getDeclaredField("defaultPolicy");
                f.setAccessible(true);
                Field mf = Field.class.getDeclaredField("modifiers");
                mf.setAccessible(true);
                mf.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                f.set(null, allPermissions);

                newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES");
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(errorString, e);
        }

        if (newMaxKeyLength < 256)
        {
            throw new RuntimeException(errorString); // hack failed
        }
    }

    static
    {
        fixKeyLength();

        try
        {
            // No padding because we're checking padding in UE-ported code manually
            AES256Cipher = Cipher.getInstance("AES/ECB/NoPadding");
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Class representing a 256 bit AES key
     */
    @FStruct
    public static class FAESKey
    {
        @StaticSize(32)
        public byte[] Key = new byte[32];

        public FAESKey()
        {
            Reset();
        }

        public boolean IsValid()
        {
            final ByteBuffer Words = ByteBuffer.wrap(Key).order(ByteOrder.LITTLE_ENDIAN);
            for (int Index = 0; Index < sizeof(Key) / 4; ++Index)
            {
                if (Words.getInt(Index) != 0)
                {
                    return true;
                }
            }
            return false;
        }

        void Reset()
        {
            FMemory.Memset(Key, 0, sizeof(Key));
        }
    }


    /**
     * Encrypts a chunk of data using a specific key
     *
     * @param Contents the buffer to encrypt
     * @param NumBytes the size of the buffer
     * @param Key An FAESKey object containing the encryption key
     */
    public static void EncryptData(byte[] Contents, int NumBytes, FAESKey Key)
    {
        checkf(Key.IsValid(), TEXT("No valid encryption key specified"));
        EncryptData(Contents, NumBytes, Key.Key, 0, sizeof(Key.Key));
    }

    /**
     * Encrypts a chunk of data using a specific key
     *
     * @param Contents the buffer to encrypt
     * @param NumBytes the size of the buffer
     * @param KeyBytes a byte array that is a 32 byte multiple length
     */
    public static void EncryptData(byte[] Contents, int NumBytes, byte[] KeyBytes, int KeyOffset, int NumKeyBytes)
    {
        checkf((NumBytes & (AESBlockSize - 1)) == 0, TEXT("NumBytes needs to be a multiple of 16 bytes"));
        checkf(NumKeyBytes >= KEYLENGTH(AES_KEYBITS), TEXT("AES key needs to be at least %d characters"), KEYLENGTH(AES_KEYBITS));

        //
        final SecretKeySpec secretKeySpec = new SecretKeySpec(KeyBytes, KeyOffset, NumKeyBytes, "AES");
        try {
            AES256Cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

            // Decrypt the data a block at a time
            for (int Offset = 0; Offset < NumBytes; Offset += AESBlockSize)
            {
                // Update and copy to the EncryptionDecryptionBuffer
                AES256Cipher.update(Contents, Offset, AESBlockSize, EncryptionDecryptionBuffer);

                // Copy to the initial array
                System.arraycopy(EncryptionDecryptionBuffer, 0, Contents, Offset, AESBlockSize);
            }

            FMemory.Memset(EncryptionDecryptionBuffer, 0, sizeof(EncryptionDecryptionBuffer));
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypts a chunk of data using a specific key
     *
     * @param Contents the buffer to encrypt
     * @param NumBytes the size of the buffer
     * @param Key a null terminated string that is a 32 byte multiple length
     */
    public static void DecryptData(byte[] Contents, int NumBytes, FAESKey Key)
    {
        checkf(Key.IsValid(), TEXT("No valid decryption key specified"));
        DecryptData(Contents, NumBytes, Key.Key, 0, sizeof(Key.Key));
    }

    /**
     * Decrypts a chunk of data using a specific key
     *
     * @param Contents the buffer to encrypt
     * @param NumBytes the size of the buffer
     * @param KeyBytes a null terminated string that is a 32 byte multiple length
     */
    public static void DecryptData(byte[] Contents, int NumBytes, byte[] KeyBytes)
    {
        DecryptData(Contents, NumBytes, KeyBytes, 0, KeyBytes.length);
    }

    /**
     * Decrypts a chunk of data using a specific key
     *
     * @param Contents the buffer to encrypt
     * @param NumBytes the size of the buffer
     * @param KeyBytes a null terminated string that is a 32 byte multiple length
     */
    public static void DecryptData(byte[] Contents, int NumBytes, byte[] KeyBytes, int KeyOffset, int NumKeyBytes)
    {
        checkf((NumBytes & (AESBlockSize - 1)) == 0, TEXT("NumBytes needs to tbe a multiple of 16 bytes"));
        checkf(NumKeyBytes >= KEYLENGTH(AES_KEYBITS), TEXT("AES key needs to be at least %d characters"), KEYLENGTH(AES_KEYBITS));

        final SecretKeySpec secretKeySpec = new SecretKeySpec(KeyBytes, KeyOffset, NumKeyBytes, "AES");
        try {
            AES256Cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            // Decrypt the data a block at a time
            for (int Offset = 0; Offset < NumBytes; Offset += AESBlockSize)
            {
                // Update and copy to the EncryptionDecryptionBuffer
                AES256Cipher.update(Contents, Offset, AESBlockSize, EncryptionDecryptionBuffer);

                // Copy to the initial array
                System.arraycopy(EncryptionDecryptionBuffer, 0, Contents, Offset, AESBlockSize);
            }

            FMemory.Memset(EncryptionDecryptionBuffer, 0, sizeof(EncryptionDecryptionBuffer));
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
