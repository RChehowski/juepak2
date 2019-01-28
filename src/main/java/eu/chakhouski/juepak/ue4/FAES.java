package eu.chakhouski.juepak.ue4;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

import static eu.chakhouski.juepak.util.Misc.TEXT;
import static eu.chakhouski.juepak.util.Misc.checkf;

@SuppressWarnings({"WeakerAccess", "SpellCheckingInspection"})
public final class FAES
{
    // A static cipher instance, all methods must vbe synchronized
    private static final Cipher AES256Cipher;

    // UE4 uses an AES encryption with ECB approach to reduce
    private static final String cryptoAlgorithmName = "AES";
    private static final String cipherTransformation = cryptoAlgorithmName + "/ECB/NoPadding";

    // Constants for AES
    private static final int AES_KEYBITS = 256;
    private static final int KEYLENGTH = AES_KEYBITS / Byte.SIZE;
    private static final int AESBlockSize = 16;

    // Buffer for decryption
    private static final byte[] EncryptionDecryptionBuffer = new byte[AESBlockSize];

    static {
        tryFixKeyLength();

        try {
            AES256Cipher = Cipher.getInstance(cipherTransformation);
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private FAES()
    {
        throw new AssertionError("No " + getClass() + " instances for you");
    }

    /**
     * Encrypts a chunk of data using a specific key
     *
     * @param Contents the buffer to encrypt
     * @param NumBytes the size of the buffer
     * @param KeyBytes a byte array that is a 32 byte multiple length
     */
    public static synchronized void EncryptData(byte[] Contents, int NumBytes, byte[] KeyBytes)
    {
        EncryptData(Contents, NumBytes, KeyBytes, 0, KeyBytes.length);
    }

    /**
     * Encrypts a chunk of data using a specific key
     *
     * @param Contents the buffer to encrypt
     * @param NumBytes the size of the buffer
     * @param KeyBytes a byte array that is a 32 byte multiple length
     */
    public static synchronized void EncryptData(byte[] Contents, int NumBytes, byte[] KeyBytes, int KeyOffset, int NumKeyBytes)
    {
        checkf((NumBytes & (AESBlockSize - 1)) == 0, TEXT("NumBytes needs to be a multiple of 16 bytes"));
        checkf(NumKeyBytes >= KEYLENGTH, TEXT("AES key needs to be at least %d characters"), KEYLENGTH);

        //
        final SecretKeySpec secretKeySpec = new SecretKeySpec(KeyBytes, KeyOffset, NumKeyBytes, cryptoAlgorithmName);
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

            Arrays.fill(EncryptionDecryptionBuffer, (byte) 0);
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
     * @param KeyBytes a null terminated string that is a 32 byte multiple length
     */
    public static synchronized void DecryptData(byte[] Contents, int NumBytes, byte[] KeyBytes)
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
    public static synchronized void DecryptData(byte[] Contents, int NumBytes, byte[] KeyBytes, int KeyOffset, int NumKeyBytes)
    {
        checkf((NumBytes & (AESBlockSize - 1)) == 0, TEXT("NumBytes needs to tbe a multiple of 16 bytes"));
        checkf(NumKeyBytes >= KEYLENGTH, TEXT("AES key needs to be at least %d characters"), KEYLENGTH);

        final SecretKeySpec secretKeySpec = new SecretKeySpec(KeyBytes, KeyOffset, NumKeyBytes, cryptoAlgorithmName);
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

            Arrays.fill(EncryptionDecryptionBuffer, (byte) 0);
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== Utility methods ====================

    /**
     * Retrieves block size.
     *
     * @return Block size in bytes.
     */
    public static int getBlockSize()
    {
        return AESBlockSize;
    }

    /**
     * In earlier JDK version AES key length is limited by 128 bit, but UE4 uses a
     */
    @SuppressWarnings("unchecked")
    private static void tryFixKeyLength()
    {
        try {
            final Class<?> classCryptoAllPermissionCollection = Class.forName("javax.crypto.CryptoAllPermissionCollection");
            final Class<?> classCryproPermissions = Class.forName("javax.crypto.CryptoPermissions");
            final Class<?> classJceSecurityManager = Class.forName("javax.crypto.JceSecurityManager");

            final int keyLengthBefore = Cipher.getMaxAllowedKeyLength(cryptoAlgorithmName);
            if (keyLengthBefore < AES_KEYBITS)
            {
                // 1.
                final Constructor con1 = classCryptoAllPermissionCollection.getDeclaredConstructor();
                con1.setAccessible(true);
                final Field all_allowed = classCryptoAllPermissionCollection.getDeclaredField("all_allowed");
                all_allowed.setAccessible(true);
                final Object cryptoAllPermissionCollection = con1.newInstance();
                all_allowed.setBoolean(cryptoAllPermissionCollection, true);

                // 2.
                final Constructor con2 = classCryproPermissions.getDeclaredConstructor();
                con2.setAccessible(true);
                Object allPermissions = con2.newInstance();
                final Field f2 = classCryproPermissions.getDeclaredField("perms");
                f2.setAccessible(true);
                ((Map) f2.get(allPermissions)).put("*", cryptoAllPermissionCollection);

                // 3.
                final Field defaultPolicyField = classJceSecurityManager.getDeclaredField("defaultPolicy");
                defaultPolicyField.setAccessible(true);
                final Field mf = Field.class.getDeclaredField("modifiers");
                mf.setAccessible(true);
                mf.setInt(defaultPolicyField, defaultPolicyField.getModifiers() & ~Modifier.FINAL);
                mf.setAccessible(false);
                defaultPolicyField.set(null, allPermissions);

                // Check whether the hack succeeded
                final int keyLengthAfter = Cipher.getMaxAllowedKeyLength(cryptoAlgorithmName);
                if (keyLengthAfter < AES_KEYBITS)
                {
                    throw new RuntimeException(String.join(System.lineSeparator(), String.join(
                            "Failed manually overriding key-length permissions.",
                            "Previous length: " + keyLengthBefore,
                            "Current length : " + keyLengthAfter
                    )));
                }
            }
        }
        catch (ReflectiveOperationException | GeneralSecurityException roe) {
            throw new RuntimeException(roe);
        }
    }
}
