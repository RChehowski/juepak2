package eu.chakhouski.juepak.ue4;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FCoreDelegates
{
    // Callback for accessing pak encryption key, if it exists
    public static final class FPakEncryptionKeyDelegate
    {
        private Consumer<byte[]> Payload = null;

        public boolean IsBound()
        {
            return Payload != null;
        }

        public void Execute(byte[] Data)
        {
            Payload.accept(Data);
        }

        public void BindLambda(Consumer<byte[]> payload)
        {
            Payload = payload;
        }
    }

    private static final FPakEncryptionKeyDelegate PakEncryptionKeyDelegate = new FPakEncryptionKeyDelegate();


    public static FPakEncryptionKeyDelegate GetPakEncryptionKeyDelegate()
    {
        return PakEncryptionKeyDelegate;
    }
}
