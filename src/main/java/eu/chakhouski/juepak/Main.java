package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FString;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Consumer;

import static eu.chakhouski.juepak.util.Misc.BOOL;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(new Consumer<byte[]>()
        {
            @Override
            public void accept(byte[] bytes)
            {
                final byte[] decode = Base64.getDecoder().decode("55K1xvTGDiR9Sz1lQtY/eCDOIIHvsVyIg1WGXRvUh58=");

                System.arraycopy(decode, 0, bytes, 0, bytes.length);
            }
        });



        final String file = "C:\\Users\\ASUS\\Desktop\\WindowsNoEditor\\FPS\\Content\\Paks\\FPS-WindowsNoEditor.pak";

        try (final FPakFile fPakFile = new FPakFile(file))
        {
            final byte[] sha1bytes = fPakFile.BriefChecksumOfContent();

            for (FFileIterator It = fPakFile.iterator(); It.hasNext();)
            {
                final FPakEntry Entry = It.next();
                final String Filename = It.Filename();

                System.out.println(String.join(System.lineSeparator(), Arrays.asList(
                        "Extracting \"" + Filename + "\"",
                        " > compression: " + ECompressionFlags.StaticToString(Entry.CompressionMethod),
                        " > offset:      " + Entry.Offset + " bytes",
                        " > encrypted:   " + (BOOL(Entry.bEncrypted) ? "yes" : "no"),
                        " > size:        " + Entry.Size + " bytes",
                        " > raw size:    " + Entry.UncompressedSize + " bytes",
                        " > sha1:        " + FString.BytesToHex(Entry.Hash)
                )));

//                final byte[] bytes = new byte[(int)Entry.UncompressedSize];
//                It.extractToMemory(bytes);

                It.extractMixed("C:\\Users\\ASUS\\Desktop\\Extract");

            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
